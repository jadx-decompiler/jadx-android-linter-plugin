package jadx.plugins.linter.index.model;

import java.util.ArrayList;
import java.util.List;

public class RuleIndex {

	private String version;

	private String androidSdkRules;

	private String androidSdkConstants;

	private String additionalConstants;

	public String getVersion() {
		return version;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

	private List<Repository> repositories = new ArrayList<>();

	public void setAndroidSdkRules(final String androidSdkRules) {
		this.androidSdkRules = androidSdkRules;
	}

	public String getAndroidSdkRules() {
		return androidSdkRules;
	}

	public void setAndroidSdkConstants(String androidSdkConstants) {
		this.androidSdkConstants = androidSdkConstants;
	}

	public String getAndroidSdkConstants() {
		return androidSdkConstants;
	}

	public void setAdditionalConstants(String additionalConstants) {
		this.additionalConstants = additionalConstants;
	}

	public String getAdditionalConstants() {
		return additionalConstants;
	}

	public List<Repository> getRepositories() {
		return repositories;
	}

}
