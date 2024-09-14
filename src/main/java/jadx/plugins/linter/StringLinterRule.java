package jadx.plugins.linter;

import jadx.core.dex.instructions.args.ArgType;

class StringLinterRule extends LinterRule<String> {
	public StringLinterRule(final String methodSignature, final int argumentOffset, final boolean flag, final String source,
			final String valueString) {
		super(methodSignature, argumentOffset, flag, source, valueString, ArgType.STRING);
	}

	@Override
	protected String convertValue(final String value) {
		return value;
	}

	@Override
	DefTypes getType() {
		return DefTypes.STRING_DEF;
	}
}
