package jadx.plugins.linter;

import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.nodes.IFieldInfoRef;

class LinterDefs implements IFieldInfoRef {

	final FieldInfo fieldInfo;

	public LinterDefs(final FieldInfo fieldInfo) {
		this.fieldInfo = fieldInfo;
	}

	@Override
	public FieldInfo getFieldInfo() {
		return fieldInfo;
	}
}
