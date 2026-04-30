package com.payu.pgsim.engine;

import com.payu.pgsim.model.ScenarioRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScenarioEngine {

    private static final Logger log = LoggerFactory.getLogger(ScenarioEngine.class);
    private static final int DEFAULT_TIMEOUT_DELAY = 30000;

    public ScenarioResult applyScenario(ScenarioRule scenario) {

        if (scenario == null || scenario.getType() == null) {
            return ScenarioResult.CONTINUE;
        }

        try {

            switch (scenario.getType().toUpperCase()) {

                case "DELAY":

                    int delay = scenario.getDelay() > 0
                            ? scenario.getDelay()
                            : 1000;

                    log.info("Applying DELAY scenario: {} ms", delay);

                    Thread.sleep(delay);
                    return ScenarioResult.CONTINUE;

                case "TIMEOUT":

                    int timeoutDelay = scenario.getDelay() > 0
                            ? scenario.getDelay()
                            : DEFAULT_TIMEOUT_DELAY;

                    log.info("Applying TIMEOUT scenario: {} ms", timeoutDelay);

                    Thread.sleep(timeoutDelay);
                    return ScenarioResult.TIMEOUT;

                case "NONE":
        return ScenarioResult.CONTINUE; 

        

                default:

                    log.warn("Unknown scenario type: {}", scenario.getType());
                    return ScenarioResult.CONTINUE;
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.warn("Scenario execution interrupted");
            return ScenarioResult.CONTINUE;
        }
    }
}