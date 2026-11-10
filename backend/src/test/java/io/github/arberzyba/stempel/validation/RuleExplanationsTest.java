package io.github.arberzyba.stempel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class RuleExplanationsTest {

    private static final Set<String> AREAS = Set.of("seller", "buyer", "lines", "totals", "payment");

    private final RuleExplanations explanations = new RuleExplanations();

    @Test
    void everyRuleHasATitleAndAText() {
        assertThat(explanations.ruleIds()).isNotEmpty().allSatisfy(id -> {
            RuleExplanations.Explanation explanation = explanations.find(id).orElseThrow();
            assertThat(explanation.title()).as(id + ".title").isNotBlank();
            assertThat(explanation.text()).as(id + ".text").isNotBlank();
            if (explanation.area() != null) {
                assertThat(AREAS).as(id + ".area").contains(explanation.area());
            }
        });
    }

    @Test
    void readsUmlautsCorrectly() {
        // Would show "KÃ¤ufer" if the file were read as ISO-8859-1 instead of UTF-8.
        assertThat(explanations.find("BR-DE-15").orElseThrow().title()).isEqualTo("Die Käuferreferenz fehlt");
    }

    @Test
    void unknownRulesHaveNoExplanation() {
        assertThat(explanations.find("BR-UNKNOWN-99")).isEmpty();
        assertThat(explanations.find("")).isEmpty();
        assertThat(explanations.find(null)).isEmpty();
    }
}
