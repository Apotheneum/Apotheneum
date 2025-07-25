/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum;

import java.io.File;
import java.util.Scanner;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.LXOscListener;
import heronarts.lx.osc.OscMessage;

@LXPlugin.Name("Apotheneum Launcher")
public class ApotheneumLauncherPlugin implements LXPlugin, LXOscListener {

  private LX lx;

  @Override
  public void initialize(LX lx) {
    this.lx = lx;
    lx.engine.osc.addListener(this);
  }

  @Override
  public void oscMessage(OscMessage message) {
    try {
      if (message.matches("/apotheneum/openProject")) {
        final String fileName = message.getString();
        final File projectFile = this.lx.getMediaFile(LX.Media.PROJECTS, fileName);
        if (!projectFile.exists()) {
          error("Requested non-existent project file: " + projectFile);
          return;
        }
        if (projectFile.equals(this.lx.getProject())) {
          log("Requested project file is already open, ignoring: " + projectFile);
          return;
        }
        // Schedule project to be loaded on next loop pass
        lx.engine.addTask(() -> {
          try {
            log("Opening project file: " + projectFile);
            this.lx.openProject(projectFile);
          } catch (Throwable x) {
            LXOscEngine.error(x, "Error opening project \"" + projectFile + "\": " + x.getMessage());
          }
        });
      } else if (message.matches("/apotheneum/openLiveProject")) {
        final String home = System.getProperty("user.home");
        final String liveProject = home + "/" + message.getString();
        new Thread(() -> {
          try {
            log("Launching Ableton project: " + liveProject);
            final Process p = Runtime.getRuntime().exec(new String[] {
              "osascript", home + "/Desktop/OpenLiveProject.scpt", liveProject }
            );
            try (Scanner scanner = new Scanner(p.getInputStream()).useDelimiter("\n")) {
              scanner.forEachRemaining(ApotheneumLauncherPlugin::log);
            }
            try (Scanner scanner = new Scanner(p.getErrorStream()).useDelimiter("\n")) {
              scanner.forEachRemaining(ApotheneumLauncherPlugin::error);
            }
          } catch (Exception x) {
            error(x, "Error on Apotheneum OSC handler: " + x.getMessage());
          }
        }).start();
      }
    } catch (Exception x) {
      error(x, "Error on Apotheneum OSC handler: " + x.getMessage());
    }
  }

  private static final String PREFIX = "[APOTHENEUM] ";

  static void log(String msg) {
    LX.log(PREFIX + msg);
  }

  static void error(String msg) {
    LX.error(PREFIX + msg);
  }

  static void error(Exception x, String msg) {
    LX.error(x, PREFIX + msg);
  }

}
