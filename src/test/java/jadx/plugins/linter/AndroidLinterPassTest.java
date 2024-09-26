package jadx.plugins.linter;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.Test;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

import static org.assertj.core.api.Assertions.assertThat;

public class AndroidLinterPassTest {
	@Test
	public void integrationTest() throws Exception {
		final JadxArgs args = new JadxArgs();
		args.getInputFiles().add(getSampleFile("lintertest.smali"));
		try (final JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			final JavaClass cls = jadx.getClasses().get(0);
			final String clsCode = cls.getCode();
			assertThat(clsCode).contains("view.setVisibility(View.VISIBLE);")
					.contains("view.setVisibility(42);")
					.contains(
							"// 1208483840 = (FLAG_RECEIVER_NO_ABORT | FLAG_RECEIVER_REGISTERED_ONLY | FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)");
		}
	}

	private File getSampleFile(final String fileName) throws URISyntaxException {
		final URL file = getClass().getClassLoader().getResource("samples/" + fileName);
		assertThat(file).isNotNull();
		return new File(file.toURI());
	}
}
