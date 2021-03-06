/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.loxone.internal.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openhab.binding.loxone.internal.core.LxJsonApp3.LxJsonControl;

/**
 * A Light Controller type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, a light controller is one of following functional blocks:
 * <ul>
 * <li>Lighting Controller
 * <li>Hotel Lighting Controller
 * </ul>
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlLightController extends LxControl implements LxControlStateListener {
    /**
     * Number of scenes supported by the Miniserver. Indexing starts with 0 to NUM_OF_SCENES-1.
     */
    public static final int NUM_OF_SCENES = 10;

    /**
     * A name by which Miniserver refers to light controller controls
     */
    private static final String TYPE_NAME = "lightcontroller";

    /**
     * Current active scene number (0-9)
     */
    private static final String STATE_ACTIVE_SCENE = "activescene";
    /**
     * List of available scenes
     */
    private static final String STATE_SCENE_LIST = "scenelist";
    /**
     * Command string used to set control's state to ON
     */
    private static final String CMD_ON = "On";
    /**
     * Command string used to set control's state to OFF
     */
    private static final String CMD_OFF = "Off";
    /**
     * Command string used to go to the next scene
     */
    private static final String CMD_NEXT_SCENE = "plus";
    /**
     * Command string used to go to the previous scene
     */
    private static final String CMD_PREVIOUS_SCENE = "minus";
    private static final int SCENE_ALL_ON = 9;

    private Map<String, String> sceneNames = new TreeMap<>();
    private boolean newSceneNames = false;
    private Integer movementScene;

    /**
     * Create lighting controller object.
     *
     * @param client
     *            communication client used to send commands to the Miniserver
     * @param uuid
     *            controller's UUID
     * @param json
     *            JSON describing the control as received from the Miniserver
     * @param room
     *            room to which controller belongs
     * @param category
     *            category to which controller belongs
     */
    LxControlLightController(LxWsClient client, LxUuid uuid, LxJsonControl json, LxContainer room,
            LxCategory category) {
        super(client, uuid, json, room, category);

        // sub-controls of this control have been created when update() method was called by super class constructor
        LxControlState sceneListState = getState(STATE_SCENE_LIST);
        if (sceneListState != null) {
            sceneListState.addListener(this);
        }
    }

    /**
     * Update Miniserver's control in runtime.
     *
     * @param json
     *            JSON describing the control as received from the Miniserver
     * @param room
     *            New room that this control belongs to
     * @param category
     *            New category that this control belongs to
     */
    @Override
    void update(LxJsonControl json, LxContainer room, LxCategory category) {
        super.update(json, room, category);

        if (json.details != null) {
            this.movementScene = json.details.movementScene;
        }

        for (LxControl control : subControls.values()) {
            control.uuid.setUpdate(false);
        }

        if (json.subControls != null) {
            for (LxJsonControl subControl : json.subControls.values()) {
                // recursively create a subcontrol as a new control
                subControl.room = json.room;
                subControl.cat = json.cat;
                LxUuid uuid = new LxUuid(subControl.uuidAction);
                if (subControls.containsKey(uuid)) {
                    subControls.get(uuid).update(subControl, room, category);
                } else {
                    LxControl control = LxControl.createControl(socketClient, uuid, subControl, room, category);
                    if (control != null) {
                        subControls.put(control.uuid, control);
                    }
                }
            }
        }
        List<LxUuid> toRemove = new ArrayList<>(subControls.size());
        for (LxControl control : subControls.values()) {
            if (!control.uuid.getUpdate()) {
                toRemove.add(control.uuid);
            }
        }
        for (LxUuid id : toRemove) {
            subControls.remove(id);
        }
    }

    /**
     * Check if control accepts provided type name from the Miniserver
     *
     * @param type
     *            name of the type received from Miniserver
     * @return
     *         true if this control is suitable for this type
     */
    public static boolean accepts(String type) {
        return type.equalsIgnoreCase(TYPE_NAME);
    }

    /**
     * Set all outputs to ON.
     *
     * @throws IOException
     *             when something went wrong with communication
     */
    public void allOn() throws IOException {
        socketClient.sendAction(uuid, CMD_ON);
    }

    /**
     * Set all outputs to OFF.
     *
     * @throws IOException
     *             when something went wrong with communication
     */
    public void allOff() throws IOException {
        socketClient.sendAction(uuid, CMD_OFF);
    }

    /**
     * Select next lighting scene.
     *
     * @throws IOException
     *             when something went wrong with communication
     */
    public void nextScene() throws IOException {
        socketClient.sendAction(uuid, CMD_NEXT_SCENE);
    }

    /**
     * Select previous lighting scene.
     *
     * @throws IOException
     *             when something went wrong with communication
     */
    public void previousScene() throws IOException {
        socketClient.sendAction(uuid, CMD_PREVIOUS_SCENE);
    }

    /**
     * Set provided scene.
     *
     * @param scene
     *            scene number to set (0-9)
     * @throws IOException
     *             when something went wrong with communication
     */
    public void setScene(int scene) throws IOException {
        if (scene == SCENE_ALL_ON) {
            allOn();
        } else if (scene >= 0 && scene < NUM_OF_SCENES) {
            socketClient.sendAction(uuid, Long.toString(scene));
        }
    }

    /**
     * Get current active scene
     *
     * @return
     *         number of the active scene (0-9, 0-all off, 9-all on) or null if error
     */
    public Integer getCurrentScene() {
        LxControlState state = getState(STATE_ACTIVE_SCENE);
        if (state != null) {
            Double value = state.getValue();
            if (value != null) {
                return value.intValue();
            }
        }
        return null;
    }

    /**
     * Get scene designated as 'movement'
     *
     * @return
     *         number of the movement scene (0-9, 0-all off, 9-all on) or null if undefined
     */
    public Integer getMovementScene() {
        return movementScene;
    }

    /**
     * Return an array with names of all scenes, where index is scene number
     *
     * @return
     *         an array with scene names indexed by scene number
     */
    public Map<String, String> getSceneNames() {
        return sceneNames;
    }

    /**
     * Check if scene names were updated since last check.
     *
     * @return
     *         true if there are new scene names
     */
    public boolean sceneNamesUpdated() {
        boolean ret = newSceneNames;
        newSceneNames = false;
        return ret;
    }

    /**
     * Get scene names from new state value received from the Miniserver
     */
    @Override
    public void onStateChange(LxControlState state) {
        String scenesText = state.getTextValue();
        if (scenesText != null) {
            sceneNames.clear();
            String[] scenes = scenesText.split(",");
            for (String line : scenes) {
                line = line.replaceAll("\"", "");
                String[] params = line.split("=");
                if (params.length == 2) {
                    sceneNames.put(params[0], params[1]);
                }
            }
            newSceneNames = true;
        }
    }
}
