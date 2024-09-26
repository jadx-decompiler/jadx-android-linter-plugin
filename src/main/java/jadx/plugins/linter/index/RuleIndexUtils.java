package jadx.plugins.linter.index;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import jadx.commons.app.JadxCommonFiles;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.plugins.linter.index.model.Artifact;
import jadx.plugins.linter.index.model.Repository;
import jadx.plugins.linter.index.model.RepositoryIndex;
import jadx.plugins.linter.index.model.RuleIndex;

public class RuleIndexUtils {

	private static final Logger LOG = LoggerFactory.getLogger(RuleIndexUtils.class);
	public static final Gson GSON = new Gson();

	public static boolean usePackagedRules() {
		return !new File(JadxCommonFiles.getCacheDir().toFile() + File.separator + "linter", "rules-integrity.json").exists();
	}

	public static void updateRules() {
		// check if rules are already located in cache folder
		final File linterCacheFolder = new File(JadxCommonFiles.getCacheDir().toFile(), "linter");
		if (!linterCacheFolder.exists()) {
			linterCacheFolder.mkdir();
		}

		boolean copy = false;
		if (linterCacheFolder.list().length == 0) {
			copyInternalLinterFile("rules-integrity.json", linterCacheFolder);
			copyInternalRules(linterCacheFolder);
		}

		// update rule index
		checkIndexIntegrity(linterCacheFolder);
	}

	private static void checkIndexIntegrity(final File linterCacheFolder) {
		final File index = new File(linterCacheFolder, "rules-integrity.json");
		downloadLinterFile(linterCacheFolder, "rules-integrity.json");

		try (final InputStream is = new FileInputStream(index)) {
			final String indexJson = RuleIndexUtils.readStringFromStream(is);
			final RuleIndex ruleIndex = GSON.fromJson(indexJson, RuleIndex.class);

			final List<String> filesToUpdate = new ArrayList<>();

			checkFileIntegrity(linterCacheFolder, "android.txt", ruleIndex.getAndroidSdkConstants(), filesToUpdate);
			checkFileIntegrity(linterCacheFolder, "additional-constants.txt", ruleIndex.getAdditionalConstants(), filesToUpdate);
			checkFileIntegrity(linterCacheFolder, "android.xml", ruleIndex.getAndroidSdkRules(), filesToUpdate);

			for (final Repository repo : ruleIndex.getRepositories()) {
				final String repoName = repo.getName();
				final File repoIntegrityFile = new File(linterCacheFolder, repoName + "-integrity.json");
				if (repoIntegrityFile.exists()) {
					checkFileIntegrity(linterCacheFolder, repoName + "-integrity.json", repo.getIndex(), filesToUpdate);
				} else {
					filesToUpdate.add(repoName + "-integrity.json");
				}
				final File repoFolder = new File(linterCacheFolder, repoName);
				if (!repoFolder.exists()) {
					repoFolder.mkdirs();
				}
			}
			for (final String f : filesToUpdate) {
				downloadLinterFile(linterCacheFolder, f);
			}
			filesToUpdate.clear();

			for (final Repository repo : ruleIndex.getRepositories()) {
				checkRepoIntegrity(linterCacheFolder, repo.getName(), filesToUpdate);
			}

			for (final String f : filesToUpdate) {
				downloadLinterFile(linterCacheFolder, f);
			}
		} catch (final IOException e) {
			throw new JadxRuntimeException("Failed to check linter index integrity", e);
		}
	}

	private static void downloadLinterFile(final File targetDir, final String filename) {
		try {
			final URL indexURL = new URL(
					"https://raw.githubusercontent.com/jadx-decompiler/jadx-android-linter-plugin/main/src/main/resources/linter/"
							+ filename);
			try (final InputStream is = indexURL.openStream();
					final FileOutputStream out = new FileOutputStream(new File(targetDir, filename))) {
				out.write(is.readAllBytes());
			} catch (final IOException e) {
				LOG.error("Could not update rule index", e);
			}
		} catch (final MalformedURLException e) {
			LOG.error("Could not update rule index", e);
		}
	}

	private static void checkRepoIntegrity(final File linterCacheFolder, final String repoToUpdate, final List<String> filesToUpdate) {
		try {
			final String repoIndexJson =
					RuleIndexUtils.readStringFromStream(new FileInputStream(new File(linterCacheFolder, repoToUpdate + "-integrity.json")));
			final RepositoryIndex repositoryIndex = GSON.fromJson(repoIndexJson, RepositoryIndex.class);

			for (final Artifact lib : repositoryIndex.getArtifacts()) {
				checkFileIntegrity(linterCacheFolder, repoToUpdate + File.separator + lib.getName() + ".xml", lib.getRules(),
						filesToUpdate);
				if (lib.getConstants() != null) {
					checkFileIntegrity(linterCacheFolder, repoToUpdate + File.separator + lib.getName() + ".txt", lib.getConstants(),
							filesToUpdate);
				}
			}
		} catch (final IOException e) {
			throw new JadxRuntimeException("Failed to check linter repository integrity", e);
		}
	}

	private static void checkFileIntegrity(final File linterCacheFolder, final String relPath, final String referenceHash,
			final List<String> filesToUpdate) {
		final File file = new File(linterCacheFolder, relPath);
		if (!file.exists()) {
			filesToUpdate.add(relPath);
			return;
		}
		final String hash = generateHash(file.getAbsolutePath());
		if (referenceHash != null && !referenceHash.equals(hash)) {
			filesToUpdate.add(relPath);
		}
	}

	private static void copyInternalLinterFile(final String filename, final File linterCacheFolder) {
		try {
			final InputStream is = getLinterFileAsStream(filename);
			if (is != null) {
				FileUtils.copyInputStreamToFile(is, new File(linterCacheFolder, filename.replace('/', File.separatorChar)));
			}
		} catch (final IOException e) {
			LOG.warn("Could not copy linter file: {}", filename);
		}
	}

	private static void copyInternalRules(final File linterCacheFolder) {
		final StringBuilder sb = new StringBuilder();

		try (final InputStream is = getRuleIndexAsStream()) {
			final String content = readStringFromStream(is);
			final RuleIndex ruleIndex = GSON.fromJson(content, RuleIndex.class);
			copyInternalLinterFile("android.txt", linterCacheFolder);
			copyInternalLinterFile("additional-constants.txt", linterCacheFolder);
			copyInternalLinterFile("android.xml", linterCacheFolder);
			for (final Repository repo : ruleIndex.getRepositories()) {
				copyInternalLinterFile(repo.getName() + "-integrity.json", linterCacheFolder);
				final File repoFolder = new File(linterCacheFolder, repo.getName());
				repoFolder.mkdir();
				copyRepoRules(repo.getName(), linterCacheFolder);
			}
		} catch (final IOException e) {
			throw new JadxRuntimeException("", e);
		}
	}

	private static void copyRepoRules(final String repo, final File repoCacheFolder) {
		final StringBuilder sb = new StringBuilder();

		try (final InputStream is = getLinterFileAsStream(repo + "-integrity.json")) {
			final String content = readStringFromStream(is);
			final RepositoryIndex repositoryIndex = GSON.fromJson(content, RepositoryIndex.class);
			for (final Artifact lib : repositoryIndex.getArtifacts()) {
				copyInternalLinterFile(repo + "/" + lib.getName() + ".xml", repoCacheFolder);
				if (lib.getConstants() != null && !lib.getConstants().isEmpty()) {
					copyInternalLinterFile(repo + "/" + lib.getName() + ".txt", repoCacheFolder);
				}
			}
		} catch (final IOException e) {
			LOG.error("Failed to load linter constant file: ", e);
		}
	}

	private static InputStream getRuleIndexAsStream() {
		try {
			return getLinterFileAsStream("rules-integrity.json");
		} catch (final FileNotFoundException e) {
			throw new JadxRuntimeException("Failed to load linter index file: ", e);
		}
	}

	private static InputStream getLinterFileAsStream(final String filename) throws FileNotFoundException {
		return RuleIndexUtils.class.getClassLoader().getResourceAsStream("linter/" + filename);
	}

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

	public static String readStringFromStream(final InputStream is) {
		final StringBuilder sb = new StringBuilder();
		try (final Scanner sc = new Scanner(is)) {
			while (sc.hasNext()) {
				final String line = sc.nextLine();
				sb.append(line);
			}
		}
		return sb.toString();
	}
}
