/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.console;

import org.panda_lang.reposilite.Reposilite;
import picocli.CommandLine.Command;

import java.util.List;

@Command(name = "stop", aliases = "shutdown", description = "Shutdown server")
final class StopCommand implements ReposiliteCommand {

    private final Reposilite reposilite;

    StopCommand(Reposilite reposilite) {
        this.reposilite = reposilite;
    }

    @Override
    public boolean execute(List<String> output) {
        try {
            reposilite.shutdown();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return true;
    }

}
