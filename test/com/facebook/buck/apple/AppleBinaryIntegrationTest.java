/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.apple;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.testutil.integration.DebuggableTemporaryFolder;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.environment.Platform;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

public class AppleBinaryIntegrationTest {

  @Rule
  public DebuggableTemporaryFolder tmp = new DebuggableTemporaryFolder();

  @Test
  public void testAppleBinaryBuildsSomething() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "apple_binary_builds_something", tmp);
    workspace.setUp();

    workspace.runBuckCommand("build", "//Apps/TestApp:TestApp").assertSuccess();

    assertTrue(Files.exists(tmp.getRootPath().resolve(BuckConstant.GEN_DIR)));
  }

  @Test
  public void testAppleBinaryWithSystemFrameworksBuildsSomething() throws IOException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "apple_binary_with_system_frameworks_builds_something", tmp);
    workspace.setUp();

    workspace.runBuckCommand("build", "//Apps/TestApp:TestApp#macosx-x86_64").assertSuccess();

    assertTrue(Files.exists(tmp.getRootPath().resolve(BuckConstant.GEN_DIR)));
  }

}
