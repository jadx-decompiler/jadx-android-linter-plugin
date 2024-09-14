package jadx.plugins.linter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jadx.api.JadxArgs;
import jadx.core.dex.nodes.RootNode;
import jadx.plugins.linter.index.RuleIndexUtils;

class LinterRuleLoaderTest {
	@Test
	void testLinterRuleLoader() {
		final LinterRuleLoader ruleLoader = new LinterRuleLoader(new RootNode(new JadxArgs()), RuleIndexUtils.usePackagedRules());
		ruleLoader.loadRulesAndConstants();
		ruleLoader.mapConstants();
		final Map<String, List<LinterRule<?>>> linterRules = ruleLoader.getLinterRules();
		Assertions.assertTrue(linterRules.size() > 0);
	}

}
