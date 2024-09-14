package jadx.plugins.linter.google;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.plugins.linter.index.model.Repository;
import jadx.plugins.linter.index.model.RepositoryIndex;
import jadx.plugins.linter.index.model.RuleIndex;
import jadx.plugins.linter.maven.BaseMavenScraper;
import jadx.plugins.linter.update.RuleIndexTools;

public class GoogleMavenScraper extends BaseMavenScraper {

	private static final Logger LOG = LoggerFactory.getLogger(GoogleMavenScraper.class);

	private static final String MAVEN_GOOGLE_BASEURL = "https://dl.google.com/android/maven2/";

	public GoogleMavenScraper(final String baseUrl) {
		super(baseUrl);
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			usage();
			System.exit(1);
		}
		final GoogleMavenScraper scraper = new GoogleMavenScraper(MAVEN_GOOGLE_BASEURL);

		final String destDir = args[0] + File.separator + "maven-google";
		final File dest = new File(destDir);
		if (!dest.exists()) {
			dest.mkdirs();
		}

		if (args.length == 2) {
			scraper.setMirrorUrl(args[1]);
		}

		final RuleIndex ruleIndex = RuleIndexTools.loadIfExists(args[0]);

		final RepositoryIndex googleRepoIndex = new RepositoryIndex();

		final Set<String> groups = scraper.fetchMasterIndex();
		for (String group : groups) {
			final XMLInputFactory xmlInputFactory = buildXmlInputFactory();
			try {
				final URL groupIndexUrl = new URL(MAVEN_GOOGLE_BASEURL
						+ group.replace('.', '/') + "/group-index.xml");
				boolean skipFirstElement = true;
				try (final InputStream is = groupIndexUrl.openStream()) {
					final XMLEventReader reader = xmlInputFactory.createXMLEventReader(is);
					while (reader.hasNext()) {
						final XMLEvent nextEvent = reader.nextEvent();
						if (nextEvent.isStartElement()) {
							final StartElement startElement = nextEvent.asStartElement();
							final String artifact = startElement.getName().getLocalPart();
							if (skipFirstElement) {
								skipFirstElement = false;
							} else {
								final Attribute versionsAttrib = startElement.getAttributeByName(new QName("versions"));
								final String[] versions = versionsAttrib.getValue().split(",");
								scraper.fetchPom(group, artifact, versions[versions.length - 1], destDir, googleRepoIndex);
							}
						}
					}
				} catch (final IOException | XMLStreamException e) {
					LOG.error("Failed to fetch group index: ", e);
				}
			} catch (final MalformedURLException e) {
				LOG.error("Invalid group index URL: ", e);
			}
		}

		final String repoIndexFilename = args[0] + File.separator + "maven-google-integrity.json";

		RuleIndexTools.writeRepoIndex(args[0], "maven-google", googleRepoIndex);
		final Repository googleRepo = new Repository("maven-google", RuleIndexTools.generateHash(repoIndexFilename));

		RuleIndexTools.updateRepositoryIndex(ruleIndex, googleRepo);
		RuleIndexTools.writeIndex(args[0], ruleIndex);
	}

	private static void usage() {
		LOG.info("<output location> <optional mirror/proxy location for maven.google.com>");
	}

	private void fetchPom(final String group, final String artifact, final String version, final String destDir,
			final RepositoryIndex googleRepoIndex) {
		try {
			final String baseUrl = getEffectiveBaseUrl();
			final URL url = new URL(baseUrl + group.replace('.', '/') + "/" + artifact + "/" + version
					+ "/" + artifact + "-" + version + ".pom");
			try (final InputStream is = url.openStream()) {
				final XMLEventReader reader = buildXmlInputFactory().createXMLEventReader(is);
				boolean isPackage = false;
				while (reader.hasNext()) {
					final XMLEvent nextEvent = reader.nextEvent();
					if (nextEvent.isStartElement()) {
						final StartElement startElement = nextEvent.asStartElement();
						final String el = startElement.getName().getLocalPart();
						if (el.equals("packaging")) {
							isPackage = true;
						}
					}
					if (nextEvent.isCharacters() && isPackage) {
						if (nextEvent.asCharacters().getData().equals("aar")) {
							this.fetchAarArtifact(group, artifact, version, destDir, googleRepoIndex);
						}
						return;
					}
				}
			} catch (final IOException | XMLStreamException e) {
				LOG.error("Failed to process pom.xml: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid repository URL: ", e);
		}
	}

	private Set<String> fetchMasterIndex() {
		final Set<String> groups = new HashSet<>();
		try {
			final URL url = new URL(MAVEN_GOOGLE_BASEURL + "master-index.xml");
			try (final InputStream is = url.openStream()) {
				final XMLEventReader reader = buildXmlInputFactory().createXMLEventReader(is);
				while (reader.hasNext()) {
					final XMLEvent nextEvent = reader.nextEvent();
					if (nextEvent.isStartElement()) {
						final StartElement startElement = nextEvent.asStartElement();
						final String group = startElement.getName().getLocalPart();
						if (!group.equals("metadata")) {
							groups.add(group);
						}
					}
				}
			} catch (final IOException | XMLStreamException e) {
				LOG.error("Failed to fetch master index: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid master index URL: ", e);
		}
		return groups;
	}
}
