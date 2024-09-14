package jadx.plugins.linter;

import jadx.core.dex.instructions.args.ArgType;

class LongLinterRule extends LinterRule<Long> {
	public LongLinterRule(final String methodSignature, final int argumentOffset, final boolean flag, final String source,
			final String valueString) {
		super(methodSignature, argumentOffset, flag, source, valueString, ArgType.LONG);
	}

	@Override
	protected Long convertValue(final String value) {
		return Long.parseLong(value);
	}

	@Override
	DefTypes getType() {
		return DefTypes.LONG_DEF;
	}
}
