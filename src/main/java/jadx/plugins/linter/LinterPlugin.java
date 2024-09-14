package jadx.plugins.linter;

import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.api.plugins.gui.JadxGuiContext;
import jadx.plugins.linter.index.RuleIndexUtils;

public class LinterPlugin implements JadxPlugin {

	public static final String PLUGIN_ID = "android-linter";

	@Override
	public JadxPluginInfo getPluginInfo() {
		return JadxPluginInfoBuilder.pluginId(PLUGIN_ID)
				.name("Android linter plugin")
				.description("Replace constants using linter rules of Android SDK and third-party libraries")
				.provides(PLUGIN_ID)
				.build();
	}

	@Override
	public void init(JadxPluginContext context) {
		context.addPass(new AndroidLinterPass());

		final JadxGuiContext guiContext = context.getGuiContext();
		if (guiContext != null) {
			guiContext.addMenuAction("Update linter plugin rules", () -> {
				RuleIndexUtils.updateRules();
			});
		}
	}
}
