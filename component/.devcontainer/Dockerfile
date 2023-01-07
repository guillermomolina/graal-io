FROM ghcr.io/graalvm/graalvm-ce:ol8-java17-22.3.0

LABEL org.opencontainers.image.authors "Guillermo Adrián Molina <guillermoadrianmolina@hotmail.com>"

ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=100
#ARG USER_GID=$USER_UID

RUN microdnf install -y git maven unzip shadow-utils sudo
#RUN gu install native-image

# Create the user
# RUN groupadd --gid $USER_GID $USERNAME && \
RUN useradd --uid $USER_UID --gid $USER_GID -m $USERNAME && \
    echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME && \
    chmod 0440 /etc/sudoers.d/$USERNAME

RUN gu install -L /workspaces/component/io-component.jar

# WORKDIR /workspaces/component

# ENTRYPOINT ["/opt/graalvm-ce-java17-22.3.0/bin/io"]

ENV PATH /opt/graalvm-ce-java17-22.3.0/bin:${PATH}

# [Optional] Set the default user. Omit if you want to keep the default as root.
USER $USERNAME
