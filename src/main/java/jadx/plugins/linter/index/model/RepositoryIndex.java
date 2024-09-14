package jadx.plugins.linter.index.model;

import java.util.ArrayList;
import java.util.List;

public class RepositoryIndex {
	private List<Artifact> artifacts = new ArrayList<>();

	public List<Artifact> getArtifacts() {
		return artifacts;
	}

	public void setArtifacts(final List<Artifact> artifacts) {
		this.artifacts = artifacts;
	}
}
