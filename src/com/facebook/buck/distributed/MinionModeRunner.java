/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.distributed;

import com.facebook.buck.distributed.thrift.BuildId;
import com.facebook.buck.distributed.thrift.FinishedBuildingResponse;
import com.facebook.buck.distributed.thrift.GetTargetsToBuildResponse;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.BuildTargetParser;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.rules.CellPathResolver;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

public class MinionModeRunner implements DistBuildModeRunner {
  private static final Logger LOG = Logger.get(MinionModeRunner.class);

  private static final int RETRY_BACKOFF_MILLIS = 1000;

  private final String coordinatorAddress;
  private final int coordinatorPort;
  private final CellPathResolver distBuildCellPathResolver;
  private final LocalBuilder builder;
  private final BuildId stampedeBuildid;

  public MinionModeRunner(
      String coordinatorAddress,
      int coordinatorPort,
      CellPathResolver distBuildCellPathResolver,
      LocalBuilder builder,
      BuildId stampedeBuildid) {
    this.builder = builder;
    this.stampedeBuildid = stampedeBuildid;
    Preconditions.checkArgument(
        coordinatorPort > 0,
        "The coordinator's port needs to be a positive integer.");
    this.coordinatorAddress = coordinatorAddress;
    this.coordinatorPort = coordinatorPort;
    this.distBuildCellPathResolver = distBuildCellPathResolver;
  }

  @Override
  public int runAndReturnExitCode() throws IOException, InterruptedException {
    try (ThriftCoordinatorClient client =
             new ThriftCoordinatorClient(coordinatorAddress, coordinatorPort, stampedeBuildid)) {
      client.start();
      final String minionId = generateNewMinionId();
      while (true) {
        GetTargetsToBuildResponse response = client.getTargetsToBuild(minionId);
        switch (response.getAction()) {
          case BUILD_TARGETS:
            List<BuildTarget> targetsToBuild =
                fullyQualifiedNameToBuildTarget(response.getBuildTargets());
            LOG.debug(String.format(
                "Minion [%s] is about to build [%d] targets: [%s]",
                minionId,
                targetsToBuild.size(),
                Joiner.on(", ").join(targetsToBuild)));
            int buildExitCode = builder.buildLocallyAndReturnExitCode(targetsToBuild);
            LOG.debug(String.format(
                "Minion [%s] finished with exit code [%d].",
                minionId,
                buildExitCode));
            FinishedBuildingResponse finishedResponse = client.finishedBuilding(
                minionId,
                buildExitCode);
            if (!finishedResponse.isContinueBuilding()) {
              return 0;
            }
            break;

          case RETRY_LATER:
            try {
              Thread.sleep(RETRY_BACKOFF_MILLIS);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            break;

          case CLOSE_CLIENT:
            return 0;

          case UNKNOWN:
          default:
            throw new RuntimeException(String.format(
                "CoordinatorClient received unexpected action [%s].",
                response.getAction()));
        }
      }
    }
  }

  public static String generateNewMinionId() throws UnknownHostException {
    String hostname = "Unknown";
    try {
      InetAddress addr;
      addr = InetAddress.getLocalHost();
      hostname = addr.getHostName();
    } catch (UnknownHostException ex) {
      System.out.println("Hostname can not be resolved");
    }

    return String.format("minion:%s:%d", hostname, new Random().nextInt(Integer.MAX_VALUE));
  }

  private List<BuildTarget> fullyQualifiedNameToBuildTarget(List<String> buildTargets) {
    List<BuildTarget> targets = Lists.newArrayList();
    for (String fullyQualifiedBuildTarget : buildTargets) {
      // TODO(ruibm): Update this to use Shivanker's static method implementation of the same thing.
      BuildTarget target = BuildTargetParser.INSTANCE.parse(
          fullyQualifiedBuildTarget,
          BuildTargetPatternParser.fullyQualified(),
          distBuildCellPathResolver);
      targets.add(target);
    }

    return targets;
  }

}