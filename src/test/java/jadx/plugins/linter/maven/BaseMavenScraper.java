package jadx.plugins.linter.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.plugins.linter.AndroidAnnotationReader;
import jadx.plugins.linter.index.model.Artifact;
import jadx.plugins.linter.index.model.RepositoryIndex;
import jadx.plugins.linter.update.RuleIndexTools;

public class BaseMavenScraper {

	private static final Logger LOG = LoggerFactory.getLogger(BaseMavenScraper.class);

	final String baseUrl;

	private String mirrorUrl = null;

	private final AndroidAnnotationReader androidAnnotationReader = new AndroidAnnotationReader();

	public BaseMavenScraper(final String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setMirrorUrl(String mirrorUrl) {
		this.mirrorUrl = mirrorUrl;
	}

	private static XMLInputFactory xmlInputFactory = null;

	protected static XMLInputFactory buildXmlInputFactory() throws FactoryConfigurationError {
		if (xmlInputFactory == null) {
			xmlInputFactory = XMLInputFactory.newInstance();
			xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		}
		return xmlInputFactory;
	}

	protected void fetchAarArtifact(final String group, final String artifact, final String version, final String destDir,
			final RepositoryIndex googleRepoIndex) {
		final String baseUrl = getEffectiveBaseUrl();
		try {
			final URL url = new URL(baseUrl + group.replace('.', '/') + "/" + artifact + "/" + version
					+ "/" + artifact + "-" + version + ".aar");
			try (final InputStream is = url.openStream()) {
				if (androidAnnotationReader.extractAarLinterRules(group, artifact, is, destDir)) {
					final File ruleFile = new File(destDir + File.separator + group + "_" + artifact + ".xml");
					if (!ruleFile.exists()) {
						return;
					}
					final Artifact a = new Artifact();
					a.setName(group + "_" + artifact);
					a.setRules(RuleIndexTools.generateHash(destDir + File.separator + group + "_" + artifact + ".xml"));
					final File constantFile = new File(destDir + File.separator + group + "_" + artifact + ".txt");
					if (constantFile.exists()) {
						if (constantFile.length() > 0) {
							a.setConstants(RuleIndexTools.generateHash(destDir + File.separator + group + "_" + artifact + ".txt"));
						} else {
							constantFile.delete();
						}
					}
					googleRepoIndex.getArtifacts().add(a);
				}
			} catch (final IOException e) {
				LOG.error("Failed to fetch aar: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid repository URL: ", e);
		}
	}

	public String getEffectiveBaseUrl() {
		return mirrorUrl != null ? mirrorUrl : baseUrl;
	}
}
