package jadx.plugins.linter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.data.ICodeComment;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxDecompilePass;
import jadx.core.codegen.utils.CodeComment;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.instructions.ConstStringNode;
import jadx.core.dex.instructions.IndexInsnNode;
import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.instructions.InvokeNode;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.InsnArg;
import jadx.core.dex.instructions.args.LiteralArg;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.instructions.args.SSAVar;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.IFieldInfoRef;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.ModVisitor;
import jadx.core.utils.InsnRemover;
import jadx.plugins.linter.index.RuleIndexUtils;

public class AndroidLinterPass implements JadxDecompilePass {

	private static final Logger LOG = LoggerFactory.getLogger(AndroidLinterPass.class);

	private RootNode root = null;

	private Map<String, List<LinterRule<?>>> linterRules = null;

	/*
	 * TODO: Open a new feature request: Add a Jadx API to collect dependency information (artifact,
	 * group, repo, version range),
	 * allow plugins to access or contribute dependencies, write dependencies to gradle files during
	 * export (as comment in dependency section). Could a plugin provide such an API?
	 */
	private final Set<String> dependencies = new HashSet<>();

	@Override
	public JadxPassInfo getInfo() {
		return new OrderedJadxPassInfo("AndroidLinterPass", "Replace constants using linter rules from SDK and third party libs",
				List.of("SSATransform", "MarkFinallyVisitor"), List.of("ConstInlineVisitor"));
	}

	@Override
	public boolean visit(ClassNode cls) {
		return true;
	}

	@Override
	public void init(final RootNode root) {
		this.root = root;

		final LinterRuleLoader ruleLoader = new LinterRuleLoader(root, RuleIndexUtils.usePackagedRules());
		ruleLoader.loadRulesAndConstants();
		ruleLoader.mapConstants();
		this.linterRules = ruleLoader.getLinterRules();
		LOG.debug("{} linter rules loaded", this.linterRules.size());
	}

	@Override
	public void visit(final MethodNode mth) {
		if (mth.isNoCode()) {
			return;
		}
		process(mth);
	}

	public void process(final MethodNode mth) {
		final List<InsnNode> toRemove = new ArrayList<>();
		for (final BlockNode block : mth.getBasicBlocks()) {
			toRemove.clear();
			for (final InsnNode insn : block.getInstructions()) {
				checkInsn(mth, insn, toRemove);
			}
			InsnRemover.removeAllAndUnbind(mth, block, toRemove);
		}
	}

	private void checkInsn(final MethodNode mth, final InsnNode insn, final List<InsnNode> toRemove) {
		if (insn.getType() == InsnType.INVOKE) {
			final InvokeNode invokeNode = (InvokeNode) insn;
			final MethodInfo callMth = invokeNode.getCallMth();
			final ClassInfo classNode = callMth.getDeclClass();

			final Set<String> classList = new HashSet<>();
			classList.add(classNode.getFullName());
			for (final String superType : root.getClsp().getSuperTypes(classNode.getFullName())) {
				classList.add(superType.replace('$', '.'));
			}

			for (final String superClass : classList) {
				final String methodKey = buildMethodKey(superClass, callMth);
				if (checkForRules(mth, toRemove, invokeNode, methodKey)) {
					return;
				}
			}
		}
	}

	private boolean checkForRules(final MethodNode mth, final List<InsnNode> toRemove, final InvokeNode invokeNode,
			final String methodKey) {
		if (linterRules.containsKey(methodKey)) {
			final List<LinterRule<?>> rules = linterRules.get(methodKey);
			if (rules != null) {
				return probeRules(mth, rules, toRemove, invokeNode);
			}
		}
		return false;
	}

	private boolean probeRules(final MethodNode mth, List<LinterRule<?>> rules, final List<InsnNode> toRemove,
			final InvokeNode invokeNode) {
		for (final LinterRule<?> rule : rules) {
			int i = 0;
			for (final InsnArg arg : invokeNode.getArguments()) {
				if (rule.getArgumentOffset() + 1 == i && arg instanceof RegisterArg) {
					return probeRule(mth, toRemove, invokeNode, rule, (RegisterArg) arg);
				}
				i++;
			}
		}
		return false;
	}

	private boolean probeRule(final MethodNode mth, final List<InsnNode> toRemove, final InvokeNode invokeNode,
			final LinterRule<?> rule, final RegisterArg regArg) {
		final SSAVar sVar = regArg.getSVar();
		final InsnNode parentInsn = sVar.getAssign().getParentInsn();
		if (parentInsn != null) {
			final InsnArg insnArg;
			if (parentInsn.getType() == InsnType.CONST) {
				insnArg = parentInsn.getArg(0);
			} else {
				if (parentInsn.getType() == InsnType.CONST_STR) {
					final InsnNode copy = parentInsn.copyWithoutResult();
					insnArg = InsnArg.wrapArg(copy);
				} else {
					return false;
				}
			}

			// all check passed, run replace
			if (replaceConst(mth, regArg, parentInsn, insnArg, invokeNode, rule)) {
				toRemove.add(parentInsn);
			}
			final String source = rule.getSource();
			if (!source.equals("Android SDK") && !dependencies.contains(source)) {
				LOG.info("Detected dependency: {}", source);
				dependencies.add(source);
			}
		}
		return true;
	}

	private static String buildMethodKey(final String fullName, final MethodInfo callMth) {
		final StringBuilder sb = new StringBuilder();
		sb.append(fullName).append(' ').append(callMth.getReturnType().toString()).append(' ').append(callMth.getName()).append('(');
		for (int i = 0; i < callMth.getArgumentsTypes().size(); i++) {
			final ArgType argType = callMth.getArgumentsTypes().get(i);
			sb.append(argType.toString());
			if (i < callMth.getArgumentsTypes().size() - 1) {
				sb.append(", ");
			}
		}
		sb.append(')');
		return sb.toString();
	}

	private static boolean replaceConst(final MethodNode mth, final RegisterArg arg, final InsnNode constInsn, final InsnArg constArg,
			final InsnNode useInsn, final LinterRule<?> rule) {
		final SSAVar ssaVar = constInsn.getResult().getSVar();
		if (ssaVar.getUseCount() == 0) {
			return true;
		}
		final List<RegisterArg> useList = new ArrayList<>(ssaVar.getUseList());
		int replaceCount = 0;
		for (final RegisterArg rArg : useList) {
			if (canInline(rArg) && replaceArg(mth, arg, constArg, constInsn, useInsn, rule)) {
				replaceCount++;
			}
		}
		if (replaceCount == useList.size()) {
			return true;
		}
		// hide insn if used only in not generated insns
		if (ssaVar.getUseList().stream().allMatch(AndroidLinterPass::canIgnoreInsn)) {
			constInsn.add(AFlag.DONT_GENERATE);
		}
		return false;
	}

	private static boolean replaceArg(final MethodNode mth, final RegisterArg arg, final InsnArg constArg, final InsnNode constInsn,
			final InsnNode useInsn, final LinterRule<?> rule) {
		if (constArg.isLiteral()) {
			final long literal = ((LiteralArg) constArg).getLiteral();
			ArgType argType = arg.getType();
			if (argType == ArgType.UNKNOWN) {
				argType = arg.getInitType();
			}
			if (argType.isObject() && literal != 0) {
				argType = ArgType.NARROW_NUMBERS;
			}
			final LiteralArg litArg = InsnArg.lit(literal, argType);
			litArg.copyAttributesFrom(constArg);

			IFieldInfoRef fieldNode = null;
			switch (rule.getType()) {
				case LONG_DEF:
					fieldNode = rule.getValueToReference().get(litArg.getLiteral());
					break;
				case INT_DEF:
					if (rule.getValueToReference() == null) {
						System.out.println("Debug here");
					}
					fieldNode = rule.getValueToReference().get((int) litArg.getLiteral());
					break;
			}

			if (fieldNode != null) {
				// arg replaced, made some optimizations
				if (!useInsn.replaceArg(arg, litArg)) {
					return false;
				}
				final IndexInsnNode sgetInsn = new IndexInsnNode(InsnType.SGET, fieldNode.getFieldInfo(), 0);
				if (litArg.wrapInstruction(mth, sgetInsn) != null) {
					ModVisitor.addFieldUsage(fieldNode, mth);
				}
				useInsn.inheritMetadata(constInsn);
				return true;
			} else {
				if (rule.isFlag()) { // perform constant unfolding
					unfoldConstantToORedInts(useInsn, rule, litArg);
				}
				return false;
			}
		} else {
			final String str = ((ConstStringNode) constInsn).getString();
			final IFieldInfoRef fieldNode = rule.getValueToReference().get(str);
			if (fieldNode != null) {
				final IndexInsnNode sgetInsn = new IndexInsnNode(InsnType.SGET, fieldNode.getFieldInfo(), 0);
				final InsnArg stringLitArg = InsnArg.wrapArg(sgetInsn);
				if (useInsn.replaceArg(arg, stringLitArg)) {
					ModVisitor.addFieldUsage(fieldNode, mth);
					return true;
				} else {
					LOG.warn("Linter pass could not replace constant");
					return false;
				}
			} else {
				LOG.debug("Linter rule not respected"); // Might be a false positive as we skip items without a constant identifier (having
														// just a numeric value)
				return false;
			}
		}
	}

	private static void unfoldConstantToORedInts(final InsnNode useInsn, final LinterRule<?> rule, LiteralArg litArg) {
		final List<String> constList = new ArrayList<>();
		final long constantValue = litArg.getLiteral();
		long combinedValue = 0;

		for (final Map.Entry<?, IFieldInfoRef> flag : rule.getValueToReference().entrySet()) {
			long flagValue = Long.parseLong(flag.getKey().toString());
			if ((constantValue & flagValue) != 0) {
				constList.add(flag.getValue().getFieldInfo().getName());
				combinedValue |= flagValue;
			}
		}
		if (constantValue != combinedValue) { // not all flags must be symbols, linter rules allow numeric values too
			constList.add(String.valueOf(constantValue & combinedValue));
		}

		// TODO insert insn nodes for constant expression and remove comment
		final String commentString = constantValue + " = (" + String.join(" | ", constList) + ")";
		final ICodeComment comment = new JadxCodeComment(null, commentString);
		useInsn.addAttr(AType.CODE_COMMENTS, new CodeComment(comment));
	}

	private static boolean canInline(final RegisterArg arg) {
		if (arg.contains(AFlag.DONT_INLINE_CONST) || arg.contains(AFlag.DONT_INLINE)) {
			return false;
		}
		final InsnNode parentInsn = arg.getParentInsn();
		if (parentInsn == null) {
			return false;
		}
		if (parentInsn.contains(AFlag.DONT_GENERATE)) {
			return false;
		}
		if (arg.isLinkedToOtherSsaVars() && !arg.getSVar().isUsedInPhi()) {
			// don't inline vars used in finally block
			return false;
		}
		return true;
	}

	private static boolean canIgnoreInsn(final RegisterArg reg) {
		final InsnNode parentInsn = reg.getParentInsn();
		if (parentInsn == null || parentInsn.getType() == InsnType.PHI) {
			return false;
		}
		if (reg.isLinkedToOtherSsaVars()) {
			return false;
		}
		return parentInsn.contains(AFlag.DONT_GENERATE);
	}
}
