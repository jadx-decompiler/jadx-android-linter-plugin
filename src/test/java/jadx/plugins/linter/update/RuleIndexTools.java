package jadx.plugins.linter.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.plugins.linter.index.model.Repository;
import jadx.plugins.linter.index.model.RepositoryIndex;
import jadx.plugins.linter.index.model.RuleIndex;

public class RuleIndexTools {

	private static final Logger LOG = LoggerFactory.getLogger(RuleIndexTools.class);

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	public static String generateHash(final String filename) {
		return "sha512-" + Base64.getEncoder().encodeToString(generateSha512(filename));
	}

	private static byte[] generateSha512(final String filename) {
		try {
			final File file = new File(filename);
			final byte[] bufferByte = new byte[(int) file.length()];
			final FileInputStream fis = new FileInputStream(file);
			fis.read(bufferByte);
			fis.close();
			final MessageDigest md = MessageDigest.getInstance("SHA-512");
			md.update(bufferByte);
			return md.digest();
		} catch (final IOException | NoSuchAlgorithmException e) {
			throw new JadxRuntimeException("Could not hash file " + filename, e);
		}
	}

	public static RuleIndex load(final File ruleIndexFile) {
		final StringBuilder contentBuilder = new StringBuilder();

		try (final Stream<String> stream = Files.lines(ruleIndexFile.toPath(), StandardCharsets.UTF_8)) {
			stream.forEach(s -> contentBuilder.append(s).append("\n"));
		} catch (final IOException e) {
			LOG.warn("Could not load existing rule index. Create an empty index", e);
		}

		return GSON.fromJson(contentBuilder.toString(), RuleIndex.class);
	}

	public static RuleIndex loadIfExists(final String location) {
		final File ruleIndexFile = new File(location + File.separator + "rules-integrity.json");
		if (ruleIndexFile.exists()) {
			return load(ruleIndexFile);
		} else {
			return new RuleIndex();
		}
	}

	public static void writeRepoIndex(final String location, final String reponame, final RepositoryIndex index) {
		sortArtifacts(index);
		try (final FileOutputStream fout = new FileOutputStream(location + File.separator + reponame + "-integrity.json")) {
			fout.write(GSON.toJson(index).getBytes(StandardCharsets.UTF_8));
		} catch (final IOException e) {
			LOG.error("Could not write repository index.", e);
		}
	}

	private static void sortArtifacts(final RepositoryIndex index) {
		index.getArtifacts().sort((a1, a2) -> a1.getName().compareTo(a2.getName()));
	}

	public static void writeIndex(final String location, final RuleIndex index) {
		sortRepositories(index.getRepositories());
		index.setAdditionalConstants(generateHash(location + File.separator + "additional-constants.txt"));
		try (final FileOutputStream fout = new FileOutputStream(location + File.separator + "rules-integrity.json")) {
			fout.write(GSON.toJson(index).getBytes(StandardCharsets.UTF_8));
		} catch (final IOException e) {
			LOG.error("Could not write rule index.", e);
		}
	}

	private static void sortRepositories(final List<Repository> repositories) {
		repositories.sort((r1, r2) -> r1.getName().compareTo(r2.getName()));
	}

	public static void updateRepositoryIndex(final RuleIndex ruleIndex, final Repository repo) {
		final String repoName = repo.getName();

		for (final Repository ri : ruleIndex.getRepositories()) {
			if (ri.getName().equals(repoName)) {
				ri.setIndex(repo.getIndex());
				return;
			}
		}
		ruleIndex.getRepositories().add(repo);
	}
}
