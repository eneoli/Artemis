package de.tum.in.www1.artemis.service.compass.umlmodel.parsers.v3;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.ELEMENT_OWNER;
import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.EnumUtils;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivity;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLControlFlow;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

public class ActivityDiagramParser {

    /**
     * Create an activity diagram from the model and control flow elements given as JSON objects. It parses the JSON objects to corresponding Java objects and creates an activity
     * diagram containing these UML model elements.
     *
     * @param modelElements     the model elements (UML activities and activity nodes) as JSON object
     * @param controlFlows      the control flow elements as JSON object
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML activity diagram containing the parsed model elements and control flows
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the control flow JSON objects
     */
    protected static UMLActivityDiagram buildActivityDiagramFromJSON(JsonObject modelElements, JsonObject controlFlows, long modelSubmissionId) throws IOException {
        Map<String, UMLActivityElement> umlActivityElementMap = new HashMap<>();
        Map<String, UMLActivity> umlActivityMap = new HashMap<>();
        List<UMLActivityNode> umlActivityNodeList = new ArrayList<>();
        List<UMLControlFlow> umlControlFlowList = new ArrayList<>();

        // loop over all JSON elements and create activity and activity node objects
        for (var entry : modelElements.entrySet()) {
            String id = entry.getKey();
            JsonObject element = entry.getValue().getAsJsonObject();

            String elementType = element.get(ELEMENT_TYPE).getAsString();
            String elementTypeUpperUnderscore = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, elementType);

            if (EnumUtils.isValidEnum(UMLActivityNode.UMLActivityNodeType.class, elementTypeUpperUnderscore)) {
                UMLActivityNode activityNode = parseActivityNode(elementTypeUpperUnderscore, element);
                umlActivityNodeList.add(activityNode);
                umlActivityElementMap.put(id, activityNode);
            }
            else if (UMLActivity.UML_ACTIVITY_TYPE.equals(elementType)) {
                UMLActivity activity = parseActivity(element);
                umlActivityMap.put(activity.getJSONElementID(), activity);
                umlActivityElementMap.put(id, activity);
            }
        }

        // loop over all JSON elements again to connect parent activity elements with their child elements
        for (var entry : modelElements.entrySet()) {
            JsonObject element = entry.getValue().getAsJsonObject();

            if (element.has(ELEMENT_OWNER) && !element.get(ELEMENT_OWNER).isJsonNull()) {
                String parentActivityId = element.get(ELEMENT_OWNER).getAsString();
                UMLActivity parentActivity = umlActivityMap.get(parentActivityId);
                UMLActivityElement childElement = umlActivityElementMap.get(element.get(ELEMENT_ID).getAsString());

                if (parentActivity != null && childElement != null) {
                    parentActivity.addChildElement(childElement);
                    childElement.setParentActivity(parentActivity);
                }
            }
        }

        // loop over all JSON control flow elements and create control flow objects
        for (var entry : controlFlows.entrySet()) {
            UMLControlFlow newControlFlow = parseControlFlow(entry.getValue().getAsJsonObject(), umlActivityElementMap);
            umlControlFlowList.add(newControlFlow);
        }

        return new UMLActivityDiagram(modelSubmissionId, umlActivityNodeList, new ArrayList<>(umlActivityMap.values()), umlControlFlowList);
    }

    /**
     * Parses the given JSON representation of a UML activity node to a UMLActivityNode Java object.
     *
     * @param activityNodeType the type of the activity node
     * @param activityNodeJson the JSON object containing the activity
     * @return the UMLActivityNode object parsed from the JSON object
     */
    private static UMLActivityNode parseActivityNode(String activityNodeType, JsonObject activityNodeJson) {
        UMLActivityNode.UMLActivityNodeType nodeType = UMLActivityNode.UMLActivityNodeType.valueOf(activityNodeType);
        String jsonElementId = activityNodeJson.get(ELEMENT_ID).getAsString();
        String nodeName = activityNodeJson.get(ELEMENT_NAME).getAsString();
        return new UMLActivityNode(nodeName, jsonElementId, nodeType);
    }

    /**
     * Parses the given JSON representation of a UML activity to a UMLActivity Java object.
     *
     * @param activityJson the JSON object containing the activity
     * @return the UMLActivity object parsed from the JSON object
     */
    private static UMLActivity parseActivity(JsonObject activityJson) {
        String jsonElementId = activityJson.get(ELEMENT_ID).getAsString();
        String activityName = activityJson.get(ELEMENT_NAME).getAsString();
        return new UMLActivity(activityName, new ArrayList<>(), jsonElementId);
    }

    /**
     * Parses the given JSON representation of a UML control flow to a UMLControlFlow Java object.
     *
     * @param controlFlowJson    the JSON object containing the control flow
     * @param activityElementMap a map containing all activity elements of the corresponding activity diagram, necessary for assigning source and target element of the control flow
     * @return the UMLControlFlow object parsed from the JSON object
     * @throws IOException when no activity elements could be found in the activityElementMap for the source and target ID in the JSON object
     */
    private static UMLControlFlow parseControlFlow(JsonObject controlFlowJson, Map<String, UMLActivityElement> activityElementMap) throws IOException {
        UMLActivityElement source = UMLModelParser.findElement(controlFlowJson, activityElementMap, RELATIONSHIP_SOURCE);
        UMLActivityElement target = UMLModelParser.findElement(controlFlowJson, activityElementMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            return new UMLControlFlow(source, target, controlFlowJson.get(ELEMENT_ID).getAsString());
        }
        else {
            throw new IOException("Control flow source " + source + " or target " + target + " not part of model!");
        }
    }

}
