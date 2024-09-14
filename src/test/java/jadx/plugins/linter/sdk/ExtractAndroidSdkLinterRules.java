package jadx.plugins.linter.sdk;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.plugins.linter.AndroidAnnotationReader;
import jadx.plugins.linter.index.model.RuleIndex;
import jadx.plugins.linter.update.RuleIndexTools;

public class ExtractAndroidSdkLinterRules {

	private static final Logger LOG = LoggerFactory.getLogger(ExtractAndroidSdkLinterRules.class);

	public static void main(String[] args) {

		if (args.length < 3) {
			usage();
			System.exit(1);
		}

		final String androidSdkLocation = args[0];
		final String apiLevel = args[1];
		final String destDir = args[2];
		File dest = new File(destDir);
		if (!dest.exists()) {
			dest.mkdirs();
		}

		final String zipFilePath =
				new File(androidSdkLocation, "platforms/android-" + apiLevel + "/data/annotations.zip").getAbsolutePath();
		final String xmlDest = destDir + File.separator + "android.xml";
		final String constDestFile = destDir + File.separator + "android.txt";
		final String androidJarLocation = new File(androidSdkLocation, "platforms/android-" + apiLevel + "/android.jar").getAbsolutePath();

		final AndroidAnnotationReader annotationReader = new AndroidAnnotationReader();
		annotationReader.processRulesWithConstants(zipFilePath, xmlDest, constDestFile, androidJarLocation);

		final RuleIndex ruleIndex = RuleIndexTools.loadIfExists(destDir);
		ruleIndex.setAndroidSdkRules(RuleIndexTools.generateHash(xmlDest));
		ruleIndex.setAndroidSdkConstants(RuleIndexTools.generateHash(constDestFile));
		RuleIndexTools.writeIndex(destDir, ruleIndex);
	}

	private static void usage() {
		LOG.info("<android sdk location (base dir)> <api-level> <output location>");
	}
}
