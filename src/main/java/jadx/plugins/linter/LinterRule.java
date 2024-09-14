package jadx.plugins.linter;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.nodes.IFieldInfoRef;
import jadx.core.dex.nodes.RootNode;

abstract class LinterRule<T> {

	private static final Logger LOG = LoggerFactory.getLogger(LinterRule.class);

	protected LinterRule(final String methodSignature, final int argumentOffset, final boolean flag, final String source,
			final String constantListString, final ArgType argType) {
		this.argumentOffset = argumentOffset;
		this.methodSignature = methodSignature;
		this.flag = flag;
		this.constantListString = constantListString;
		this.source = source;
		this.argType = argType;
	}

	public Map<T, IFieldInfoRef> buildConstantMap(final Map<String, String> constantMap, final RootNode root) {
		final Map<T, IFieldInfoRef> enumMap = new HashMap<>();
		this.setConstantMap(enumMap);
		if (this.getConstantListString() == null) {
			LOG.error("No constant list found for rule '{}', argument offset {}", this.methodSignature, this.argumentOffset);
			return enumMap;
		}
		for (final String fullConstantName : this.getConstantListString().split(", ")) {
			final int idx = fullConstantName.lastIndexOf('.');
			if (idx == -1) {
				LOG.error("Ignored invalid constant {} for rule '{}', argument offset {}", fullConstantName, this.methodSignature,
						this.argumentOffset);
				continue;
			}
			final String className = fullConstantName.substring(0, idx);
			final String constantName = fullConstantName.substring(idx + 1);
			final String value = constantMap.get(fullConstantName);
			if (value != null) {
				try {
					final ClassInfo cls = ClassInfo.fromName(root, className);
					this.getValueToReference().put(convertValue(value), FieldInfo.from(root, cls, constantName, argType));
				} catch (final NumberFormatException e) {
					LOG.error("Invalid constant value found for {}", fullConstantName);
				}
			} else {
				LOG.error("No constant value found for {}", fullConstantName);
			}
		}
		return enumMap;
	}

	protected abstract T convertValue(String value);

	private final String methodSignature;

	private final int argumentOffset;

	private final boolean flag;

	private Map<T, IFieldInfoRef> valueToReference = null;

	private final String source;

	private final String constantListString;

	private final ArgType argType;

	public String getMethodSignature() {
		return methodSignature;
	}

	public int getArgumentOffset() {
		return argumentOffset;
	}

	public void setConstantMap(Map<T, IFieldInfoRef> constantMap) {
		valueToReference = constantMap;
	}

	public boolean isFlag() {
		return flag;
	}

	public Map<T, IFieldInfoRef> getValueToReference() {
		return valueToReference;
	}

	public String getSource() {
		return source;
	}

	public String getConstantListString() {
		return constantListString;
	}

	abstract DefTypes getType();
}
