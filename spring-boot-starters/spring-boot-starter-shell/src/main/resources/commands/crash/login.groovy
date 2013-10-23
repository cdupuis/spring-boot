welcome = { ->
  def hostName;
  try {
    hostName = java.net.InetAddress.getLocalHost().getHostName();
  } catch (java.net.UnknownHostException ignore) {
    hostName = "localhost";
  }
  return """\
  .   ____          _            __ _ _
 /\\\\ / ___'_ __ _ _(_)_ __  __ _ \\ \\ \\ \\
( ( )\\___ | '_ | '_| | '_ \\/ _` | \\ \\ \\ \\
 \\\\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\\__, | / / / /
 =========|_|==============|___/=/_/_/_/

Welcome to $hostName + !
It is ${new Date()} now
""";
}

prompt = { ->
  return "> ";
}
