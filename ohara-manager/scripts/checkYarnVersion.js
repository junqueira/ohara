/*
 * Copyright 2019 is-land
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

const { exec } = require('child_process');

/* eslint-disable no-console */
exec('yarn -v', (err, stdout) => {
  if (err) throw err;

  const yarnVersion = stdout.trim();
  const minor = parseFloat(yarnVersion.slice(2));

  // Since we're using yarn audit in one of our npm scripts. We need to
  // use yarn 1.13.x or greater as yarn audit was added in yarn 1.12.3
  // https://github.com/yarnpkg/yarn/issues/5808
  if (minor < 13) {
    throw new Error(
      `Ohara Manger requires yarn 1.13.0 or greater, but you're using ${yarnVersion}`,
    );
  }

  console.log(`👌 Yarn version check passed! You're using yarn ${yarnVersion}`);
  console.log('📦 Installing Ohara Manager dependencies');
});
