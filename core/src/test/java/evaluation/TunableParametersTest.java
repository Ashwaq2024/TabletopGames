package evaluation;

import evaluation.optimisation.ITPSearchSpace;
import games.puertorico.PuertoRicoActionHeuristic001;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import players.heuristics.CoarseTunableHeuristic;
import players.mcts.MCTSEnums;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.simple.BoltzmannActionParams;
import players.simple.BoltzmannActionPlayer;
import players.simple.RandomPlayer;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static players.PlayerConstants.BUDGET_TIME;
import static players.heuristics.CoarseTunableHeuristic.HeuristicType.*;

public class TunableParametersTest {

    MCTSParams params;
    BoltzmannActionParams bap;

    @Before
    public void setup() {
        params = new MCTSParams();
        bap = new BoltzmannActionParams();
        bap.setParameterValue("temperature", 0.56);
        bap.setParameterValue("epsilon", 0.27);
    }

    @Test
    public void setParameterValueUpdatesLocalValue() {
        params.setParameterValue("rolloutLength", 123);
        assertEquals(123, params.rolloutLength);
        assertEquals(123, params.getParameterValue("rolloutLength"));
    }

    @Test
    public void subParamValuesAreVisibleAtTopLevel() {
        Assert.assertTrue(params.getRolloutStrategy() instanceof RandomPlayer);
        params.setParameterValue("rolloutPolicyParams", bap);
        assertEquals(bap, params.getParameterValue("rolloutPolicyParams"));
        assertEquals(0.56, params.getParameterValue("rolloutPolicyParams.temperature"));
        assertEquals(0.27, params.getParameterValue("rolloutPolicyParams.epsilon"));
        Assert.assertFalse(params.getRolloutStrategy() instanceof BoltzmannActionPlayer);
        params.setParameterValue("rolloutType", MCTSEnums.Strategies.PARAMS);
        Assert.assertTrue(params.getRolloutStrategy() instanceof BoltzmannActionPlayer);
        BoltzmannActionPlayer rollout = (BoltzmannActionPlayer) params.getRolloutStrategy();
        assertEquals(0.56, rollout.temperature, 0.001);
        assertEquals(0.27, rollout.epsilon, 0.001);
        assertEquals(0.56, params.getParameterValue("rolloutPolicyParams.temperature"));
        assertEquals(0.27, params.getParameterValue("rolloutPolicyParams.epsilon"));
    }

    @Test
    public void subParamValuesCanBeChangedFromTopLevel() {
        Assert.assertFalse(params.getParameterNames().contains("rolloutPolicyParams.temperature"));
        Assert.assertFalse(params.getParameterNames().contains("rolloutPolicyParams.epsilon"));
        params.setParameterValue("rolloutPolicyParams", bap);
        params.setParameterValue("rolloutType", MCTSEnums.Strategies.PARAMS);
        Assert.assertTrue(params.getRolloutStrategy() instanceof BoltzmannActionPlayer);
        Assert.assertTrue(params.getParameterNames().contains("rolloutPolicyParams.temperature"));
        Assert.assertTrue(params.getParameterNames().contains("rolloutPolicyParams.epsilon"));
        params.setParameterValue("rolloutPolicyParams.temperature", 0.78);
        params.setParameterValue("rolloutPolicyParams.epsilon", 0.12);
        BoltzmannActionPlayer rollout = (BoltzmannActionPlayer) params.getRolloutStrategy();
        assertEquals(0.78, rollout.temperature, 0.001);
        assertEquals(0.12, rollout.epsilon, 0.001);
    }

    @Test
    public void copyDoesADeepCopyIncludingSubParams() {
        params.setParameterValue("rolloutPolicyParams", bap);
        MCTSParams newParams = (MCTSParams) params.copy();
        assertEquals(params, newParams);
        Assert.assertNotSame(params, newParams);
        assertEquals(bap, newParams.getParameterValue("rolloutPolicyParams"));
        Assert.assertNotSame(bap, newParams.getParameterValue("rolloutPolicyParams"));
        Assert.assertSame(bap, params.getParameterValue("rolloutPolicyParams"));
    }

    @Test
    public void setParameterValueOnCopyDoesNotUpdateOriginal() {
        params.setParameterValue("rolloutPolicyParams", bap);
        params.setParameterValue("rolloutType", MCTSEnums.Strategies.PARAMS);
        MCTSParams newParams = (MCTSParams) params.copy();
        assertEquals(params, newParams);
        Assert.assertNotSame(params, newParams);
        assertEquals(bap, newParams.getParameterValue("rolloutPolicyParams"));
        Assert.assertNotSame(bap, newParams.getParameterValue("rolloutPolicyParams"));
        Assert.assertSame(bap, params.getParameterValue("rolloutPolicyParams"));

        newParams.setParameterValue("rolloutPolicyParams.temperature", 2.0);
        Assert.assertTrue(newParams.getRolloutStrategy() instanceof BoltzmannActionPlayer);
        assertEquals(2.0, ((BoltzmannActionPlayer) newParams.getRolloutStrategy()).temperature, 0.001);
        assertEquals(0.56, ((BoltzmannActionPlayer) params.getRolloutStrategy()).temperature, 0.001);
    }

    @Test
    public void loadSearchSpaceIncludesSubParams() {
        String searchSpace = "src\\test\\java\\evaluation\\MCTSSearch_MASTRollout.json";
        ITPSearchSpace itp = new ITPSearchSpace(params, searchSpace);
        assertEquals(8, itp.getSearchKeys().size());
        int MASTBoltzmannIndex = itp.getIndexOf("MASTBoltzmann");
        int heuristicTypeIndex = itp.getIndexOf("heuristic.heuristicType");
        Assert.assertTrue(MASTBoltzmannIndex > -1);
        Assert.assertTrue(heuristicTypeIndex > -1);
        assertEquals(5, itp.getSearchValues().get(MASTBoltzmannIndex).size());
        assertEquals(Arrays.asList(0.01, 0.1, 1.0, 10.0, 100.0), itp.getSearchValues().get(MASTBoltzmannIndex));
        assertEquals(3, itp.getSearchValues().get(heuristicTypeIndex).size());
        List<CoarseTunableHeuristic.HeuristicType> expectedArray = Arrays.asList(WIN_ONLY, SCORE_PLUS, LEADER);
        assertEquals(expectedArray, itp.getSearchValues().get(heuristicTypeIndex));

        int[] settings = new int[] {0, 0, 0, 0, 0, 0, 0, 0};
        settings[heuristicTypeIndex] = 1;
        settings[MASTBoltzmannIndex] = 3;
        MCTSPlayer agent = (MCTSPlayer) itp.getAgent(settings);
        MCTSParams params = agent.getParameters();
        assertEquals(10.0, params.MASTBoltzmann, 0.001);
        Assert.assertTrue(params.getHeuristic() instanceof  CoarseTunableHeuristic);
        assertEquals(SCORE_PLUS, ((CoarseTunableHeuristic) params.heuristic).getHeuristicType());
    }

    @Test
    public void loadedSubParamsIncludesNonParameterisedObjects() {
        String searchSpace = "src\\test\\java\\evaluation\\MCTSSearch_PR_RolloutPolicy.json";
        ITPSearchSpace itp = new ITPSearchSpace(params, searchSpace);
        // actionHeuristic is a nested (non-Tunable) class, so check it is not included
        assertEquals(-1, itp.getIndexOf("rolloutPolicyParams.actionHeuristic"));
        assertEquals(-1, itp.getIndexOf("actionHeuristic"));
        // temperature should be
        int temperatureIndex = itp.getIndexOf("rolloutPolicyParams.temperature");
        Assert.assertTrue(temperatureIndex > -1);
        assertEquals(5, itp.getSearchValues().get(temperatureIndex).size());
        assertEquals(Arrays.asList(0.01, 0.1, 1.0, 10.0, 100.0), itp.getSearchValues().get(temperatureIndex));

        int[] settings = new int[] {0, 0, 0, 0, 0};

        MCTSPlayer agent = (MCTSPlayer) itp.getAgent(settings);
        MCTSParams params = agent.getParameters();
        Assert.assertTrue(params.getRolloutStrategy() instanceof  BoltzmannActionPlayer);
        BoltzmannActionPlayer rollout = (BoltzmannActionPlayer) params.getRolloutStrategy();
        assertEquals(0.01, rollout.temperature, 0.001);
        assertEquals(new PuertoRicoActionHeuristic001(), rollout.getActionHeuristic());
    }

    @Test
    public void copyingParamsChangesRandomSeedOnChildButNotParent() {
        long startingSeed = params.getRandomSeed();
        MCTSParams copy = (MCTSParams) params.copy();
        assertNotSame(startingSeed, copy.getRandomSeed());
        assertEquals(startingSeed, params.getRandomSeed());
    }

    @Test
    public void toJSONWithDefaults() {
        params.setParameterValue("rolloutPolicyParams", bap);
        params.setParameterValue("maxTreeDepth", 67);

        JSONObject json = params.instanceToJSON(false);
        assertEquals(0.56, params.getParameterValue("rolloutPolicyParams.temperature"));

        MCTSParams noChange = (MCTSParams) params.instanceFromJSON(json);
        Assert.assertTrue(params.allParametersAndValuesEqual(noChange));

        Assert.assertEquals(67, json.get("maxTreeDepth"));
        assertEquals(0.56, params.getParameterValue("rolloutPolicyParams.temperature"));
        Assert.assertEquals(0.56, ((JSONObject) json.get("rolloutPolicyParams")).get("temperature"));
        Assert.assertEquals(Math.sqrt(2), (Double) json.get("K"), 0.002);
        Assert.assertEquals(false, json.get("useMASTAsActionHeuristic"));
        Assert.assertEquals("BUDGET_FM_CALLS", json.get("budgetType"));

        assertEquals(MCTSParams.class, params.instanceFromJSON(json).getClass());
        MCTSParams fromJSON = (MCTSParams) params.instanceFromJSON(json);
        Assert.assertTrue(fromJSON.allParametersAndValuesEqual(noChange));
    }

    @Test
    public void toJSONWithoutDefaults() {
        params.setParameterValue("rolloutPolicyParams", bap);
        params.setParameterValue("maxTreeDepth", 67);
        params.setParameterValue("budgetType", BUDGET_TIME);

        JSONObject json = params.instanceToJSON(true);

        MCTSParams noChange = (MCTSParams) params.instanceFromJSON(json);
        Assert.assertTrue(params.allParametersAndValuesEqual(noChange));

        Assert.assertEquals(67, json.get("maxTreeDepth"));
        Assert.assertEquals(0.56, ((JSONObject) json.get("rolloutPolicyParams")).get("temperature"));
        Assert.assertFalse(json.containsKey("K"));
        Assert.assertFalse(json.containsKey("useMASTAsActionHeuristic"));
        Assert.assertFalse(json.containsKey("selectionPolicy"));
        Assert.assertEquals("BUDGET_TIME", json.get("budgetType"));

        MCTSParams fromJSON = (MCTSParams) params.instanceFromJSON(json);
        Assert.assertTrue(fromJSON.allParametersAndValuesEqual(noChange));
    }
}