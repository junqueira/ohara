#
# Copyright 2019 is-land
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM centos:7.6.1810 as deps

# install tools
RUN yum install -y \
  wget

# download zookeeper
# WARN: Please don't change the value of ZOOKEEPER_DIR
ARG ZOOKEEPER_DIR=/opt/zookeeper
ARG ZOOKEEPER_VERSION=3.4.13
RUN wget http://ftp.twaren.net/Unix/Web/apache/zookeeper/zookeeper-${ZOOKEEPER_VERSION}/zookeeper-${ZOOKEEPER_VERSION}.tar.gz
RUN mkdir ${ZOOKEEPER_DIR}
RUN tar -zxvf zookeeper-${ZOOKEEPER_VERSION}.tar.gz -C ${ZOOKEEPER_DIR}
RUN rm -f zookeeper-${ZOOKEEPER_VERSION}.tar.gz
RUN echo "$ZOOKEEPER_VERSION" > $(find "${ZOOKEEPER_DIR}" -maxdepth 1 -type d -name "zookeeper-*")/bin/true_version

# download Tini
ARG TINI_VERSION=v0.18.0
RUN wget https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini -O /tini

FROM centos:7.6.1810

# install tools
RUN yum install -y \
  java-1.8.0-openjdk

ENV JAVA_HOME=/usr/lib/jvm/jre

# change user
ARG USER=zookeeper
RUN groupadd $USER
RUN useradd -ms /bin/bash -g $USER $USER

# copy zookeeper binary
COPY --from=deps /opt/zookeeper /home/$USER
RUN ln -s $(find "/home/$USER" -maxdepth 1 -type d -name "zookeeper-*") /home/$USER/default
COPY ./zk.sh /home/$USER/default/bin/
RUN chown -R $USER:$USER /home/$USER
RUN chmod +x /home/$USER/default/bin/zk.sh
ENV ZOOKEEPER_HOME=/home/$USER/default
ENV PATH=$PATH:$ZOOKEEPER_HOME/bin

# copy Tini
COPY --from=deps /tini /tini
RUN chmod +x /tini

USER $USER

ENTRYPOINT ["/tini", "--", "zk.sh"]
