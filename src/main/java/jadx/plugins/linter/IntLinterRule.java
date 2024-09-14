package jadx.plugins.linter;

import jadx.core.dex.instructions.args.ArgType;

class IntLinterRule extends LinterRule<Integer> {
	public IntLinterRule(final String methodSignature, final int argumentOffset, final boolean flag, final String source,
			final String valueString) {
		super(methodSignature, argumentOffset, flag, source, valueString, ArgType.INT);
	}

	@Override
	protected Integer convertValue(final String value) {
		return Integer.parseInt(value);
	}

	@Override
	DefTypes getType() {
		return DefTypes.INT_DEF;
	}
}
