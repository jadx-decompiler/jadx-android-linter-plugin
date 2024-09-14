package jadx.plugins.linter.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.plugins.linter.index.model.Repository;
import jadx.plugins.linter.index.model.RepositoryIndex;
import jadx.plugins.linter.index.model.RuleIndex;
import jadx.plugins.linter.update.RuleIndexTools;

public class MavenScraper extends BaseMavenScraper {

	public MavenScraper(final String baseUrl) {
		super(baseUrl);
	}

	private static final Logger LOG = LoggerFactory.getLogger(MavenScraper.class);

	public static void main(final String[] args) {
		if (args.length < 2) {
			usage();
			System.exit(1);
		}

		final String repo = args[1].toLowerCase();
		final String baseUrl;

		switch (repo) {
			case "maven-central":
				baseUrl = "https://repo1.maven.org/maven2/";
				break;
			case "jitpack":
				baseUrl = "https://jitpack.io/";
				break;
			default:
				LOG.error("Unsupported repository {}", repo);
				return;
		}

		final String destDir = args[0];
		final String repoDestDir = args[0] + File.separator + repo;
		final File dest = new File(repoDestDir);
		if (!dest.exists()) {
			dest.mkdirs();
		}

		final MavenScraper scraper = new MavenScraper(baseUrl);

		final RepositoryIndex repoIndex = new RepositoryIndex();
		scraper.readArtifactList(MavenScraper.class.getClassLoader().getResourceAsStream(repo + ".txt"), dest, repoIndex);

		if (args.length == 4) {
			scraper.setMirrorUrl(args[3]);
		}

		final RuleIndex ruleIndex = RuleIndexTools.loadIfExists(destDir);
		final String repoIndexFilename = repoDestDir + "-integrity.json";
		RuleIndexTools.writeRepoIndex(destDir, repo, repoIndex);

		final Repository mavenRepo = new Repository(repo, RuleIndexTools.generateHash(repoIndexFilename));
		RuleIndexTools.updateRepositoryIndex(ruleIndex, mavenRepo);
		RuleIndexTools.writeIndex(destDir, ruleIndex);
	}

	private static void usage() {
		LOG.info("<output location> <repo one of \"maven-central\" or \"jitpack\"> <optional mirror/maven proxy repository>");
	}

	public void readArtifactList(final InputStream is, final File dest, final RepositoryIndex repoIndex) {

		String artifact;
		try (final Scanner sc = new Scanner(is)) {
			while (sc.hasNext()) {
				artifact = sc.nextLine().trim();
				if (!artifact.startsWith("#")) {
					fetchMavenAtrifact(artifact, dest, repoIndex);
				}
			}
		}
	}

	private void fetchMavenAtrifact(final String artifact, final File dest, final RepositoryIndex repoIndex) {
		final String[] parts = artifact.split(":");
		final String latestVersion = fetchIndex(parts[0], parts[1]);
		fetchAarArtifact(parts[0], parts[1], latestVersion, dest.getAbsolutePath(), repoIndex);
	}

	private String fetchIndex(final String group, final String artifact) {
		try {
			final String baseUrl = getEffectiveBaseUrl();
			final URL url = new URL(baseUrl + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml");
			try (final InputStream is = url.openStream()) {
				final XMLEventReader reader = buildXmlInputFactory().createXMLEventReader(is);
				boolean isLatest = false;
				boolean isRelease = false;
				String release = null;
				while (reader.hasNext()) {
					final XMLEvent nextEvent = reader.nextEvent();
					if (nextEvent.isStartElement()) {
						final StartElement startElement = nextEvent.asStartElement();
						final String el = startElement.getName().getLocalPart();
						if (el.equals("latest")) {
							isLatest = true;
						}
						if (el.equals("release")) {
							isRelease = true;
						}
					}
					if (nextEvent.isCharacters()) {
						if (isLatest) {
							return nextEvent.asCharacters().getData();
						}
						if (isRelease) {
							release = nextEvent.asCharacters().getData();
							isRelease = false;
						}
					}
				}
				if (release != null) {
					return release;
				}
			} catch (final IOException | XMLStreamException e) {
				LOG.error("Failed to process maven-metadata.xml: ", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Invalid repository URL: ", e);
		}
		return null;
	}
}
