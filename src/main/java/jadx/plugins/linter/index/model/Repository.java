package jadx.plugins.linter.index.model;

public class Repository {

	private String name;

	private String index;

	public Repository() {
	}

	public Repository(final String name, final String index) {
		this.name = name;
		this.index = index;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(final String index) {
		this.index = index;
	}
}
