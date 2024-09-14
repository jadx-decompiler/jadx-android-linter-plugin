package jadx.plugins.linter;

enum DefTypes {
	INT_DEF("androidx.annotation.IntDef"),
	LONG_DEF("androidx.annotation.LongDef"),
	STRING_DEF("androidx.annotation.StringDef");

	private String androidXClassName;

	DefTypes(final String androidXClassName) {
		this.androidXClassName = androidXClassName;
	}

	public String getAndroidXClassName() {
		return androidXClassName;
	}
}
