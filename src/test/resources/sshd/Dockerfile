FROM linuxserver/openssh-server
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories \
    && apk update && apk add wget tmux gcc zip p7zip g++ git make zsh htop stress-ng inetutils-telnet xclock xcalc xorg-server xinit && wget https://ohse.de/uwe/releases/lrzsz-0.12.20.tar.gz \
    && tar -xf lrzsz-0.12.20.tar.gz && cd lrzsz-0.12.20 && ./configure && make && make install \
    && ln -s /usr/local/bin/lrz /usr/local/bin/rz && ln -s /usr/local/bin/lsz /usr/local/bin/sz
RUN sed -i 's/#AllowAgentForwarding yes/AllowAgentForwarding yes/g' /etc/ssh/sshd_config
RUN sed -i 's/AllowTcpForwarding no/AllowTcpForwarding yes/g' /etc/ssh/sshd_config
RUN sed -i 's/GatewayPorts no/GatewayPorts yes/g' /etc/ssh/sshd_config
RUN sed -i 's/X11Forwarding no/X11Forwarding yes/g' /etc/ssh/sshd_config
