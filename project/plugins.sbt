scalacOptions += "-deprecation"

addSbtPlugin("com.geirsson"          % "sbt-scalafmt"   % "0.5.5")
addSbtPlugin("com.jsuereth"          % "sbt-pgp"        % "1.0.1")
addSbtPlugin("com.eed3si9n"          % "sbt-buildinfo"  % "0.6.1")
addSbtPlugin("de.heikoseeberger"     % "sbt-header"     % "1.7.0")
addSbtPlugin("com.github.gseitz"     % "sbt-release"    % "1.0.4")
addSbtPlugin("org.xerial.sbt"        % "sbt-sonatype"   % "1.1")
addSbtPlugin("org.scoverage"         % "sbt-scoverage"  % "1.5.0")
addSbtPlugin("org.scoverage"         % "sbt-coveralls"  % "1.1.0")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox"    % "0.2.8")
addSbtPlugin("com.typesafe.sbt"      % "sbt-site"       % "1.2.0")
addSbtPlugin("com.typesafe.sbt"      % "sbt-ghpages"    % "0.6.0")