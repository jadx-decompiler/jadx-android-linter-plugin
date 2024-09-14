package jadx.plugins.linter.index.model;

public class Artifact {

	private String name;

	private String rules;

	private String constants;

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getRules() {
		return rules;
	}

	public void setRules(final String rules) {
		this.rules = rules;
	}

	public String getConstants() {
		return constants;
	}

	public void setConstants(final String constants) {
		this.constants = constants;
	}
}
