import java.util.Date

lazy val ScalaVersions = Seq("2.11.11", "2.12.2")

organization in ThisBuild := "com.geirsson"
version in ThisBuild ~= { old =>
  customVersion.getOrElse(old).replace('+', '-')
}
allSettings
noPublish

commands += Command.command("release") { s =>
  "clean" ::
    "sonatypeOpen metaconfig-release" ::
    "very publishSigned" ::
    "sonatypeReleaseAll" ::
    s
}

lazy val `metaconfig-docs` = project
  .settings(
    allSettings,
    libraryDependencies ++= List(
      "com.lihaoyi" %% "scalatags" % "0.6.7"
    )
  )
  .dependsOn(`metaconfig-coreJVM`)

lazy val vork = project.settings(
  allSettings,
  fork.in(run) := true,
  TaskKey[Unit]("vork") := Def.taskDyn {
    val cp = fullClasspath
        .in(website, Compile)
        .value
        .map(_.data.getAbsolutePath)
        .filterNot(_.contains("ammonite"))
        .mkString(java.io.File.pathSeparator)
    Def.task(
      runMain
          .in(Compile)
          .toTask(
            s" vork.Cli --in ../docs --classpath $cp --exclude-files node_modules")
          .value
    )
  }.value,
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= List(
    "com.geirsson" %% "vork" % "0.1.1-3-18691269-SNAPSHOT"
  )
)
lazy val website = project
  .settings(
    allSettings,
    tutNameFilter := "README.md".r,
    tutSourceDirectory := baseDirectory.in(ThisBuild).value / "docs",
    sourceDirectory.in(Preprocess) := tutTargetDirectory.value,
    sourceDirectory.in(GitBook) := target.in(Preprocess).value,
    preprocessVars in Preprocess := Map(
      "VERSION" -> version.value.replaceAll("-.*", ""),
      "DATE" -> new Date().toString
    ),
    siteSourceDirectory := target.in(GitBook).value,
    makeSite := makeSite.dependsOn(tut, compile.in(Compile)).value,
    ghpagesPushSite := ghpagesPushSite.dependsOn(makeSite).value,
    publish := ghpagesPushSite.value,
    git.remoteRepo := "git@github.com:olafurpg/metaconfig.git"
  )
  .enablePlugins(
    GhpagesPlugin,
    PreprocessPlugin,
    GitBookPlugin,
    TutPlugin
  )
  .dependsOn(
    `metaconfig-docs`,
    `metaconfig-typesafe-config`
  )

lazy val MetaVersion = "2.0.0-M3"

lazy val baseSettings = Seq(
  scalaVersion := ScalaVersions.last,
  crossScalaVersions := ScalaVersions,
  libraryDependencies ++= List(
    "org.scalacheck" %%% "scalacheck" % "1.13.5" % Test,
    "org.scalatest" %%% "scalatest" % "3.0.2" % Test
  )
)

lazy val publishSettings = Seq(
  publishTo := Some(
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
  publishArtifact in Test := false,
  licenses := Seq(
    "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/olafurpg/metaconfig")),
  autoAPIMappings := true,
  apiURL := Some(url("https://github.com/olafurpg/metaconfig")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/olafurpg/metaconfig"),
      "scm:git:git@github.com:olafurpg/metaconfig.git"
    )
  ),
  developers +=
    Developer(
      "olafurpg",
      "Ólafur Páll Geirsson",
      "olafurpg@gmail.com",
      url("https://geirsson.com")
    )
)

lazy val allSettings = baseSettings ++ publishSettings

lazy val `metaconfig-core` = crossProject
  .settings(
    allSettings,
    // Position/Input
    libraryDependencies ++= List(
      "org.scalameta" %%% "inputs" % MetaVersion,
      "com.lihaoyi" %%% "pprint" % "0.5.3",
      "org.typelevel" %%% "paiges-core" % "0.2.0",
      scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .jvmSettings(
    mimaPreviousArtifacts := {
      // TODO(olafur) enable mima check in CI after 0.6.0 release.
      val previousArtifactVersion = "0.6.0"
      val binaryVersion =
        if (crossVersion.value.isInstanceOf[CrossVersion.Full])
          scalaVersion.value
        else scalaBinaryVersion.value
      Set(
        organization.value % s"${moduleName.value}_$binaryVersion" % previousArtifactVersion
      )
    },
    mimaBinaryIssueFilters ++= Mima.ignoredABIProblems,
    libraryDependencies += "org.scalameta" %% "testkit" % MetaVersion % Test
  )
lazy val `metaconfig-coreJVM` = `metaconfig-core`.jvm
lazy val `metaconfig-coreJS` = `metaconfig-core`.js

lazy val typesafeConfig = "com.typesafe" % "config" % "1.2.1"

lazy val `metaconfig-typesafe-config` = project
  .settings(
    allSettings,
    description := "Integration for HOCON using typesafehub/config.",
    libraryDependencies += typesafeConfig
  )
  .dependsOn(`metaconfig-coreJVM` % "test->test;compile->compile")

lazy val `metaconfig-hocon` = crossProject
  .settings(
    allSettings,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "fastparse" % "0.4.3"
    ),
    description := "EXPERIMENTAL Integration for HOCON using custom parser. On JVM, use metaconfig-typesafe-config."
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      typesafeConfig % Test
    )
  )
  .dependsOn(`metaconfig-core` % "test->test;compile->compile")
lazy val `metaconfig-hoconJVM` = `metaconfig-hocon`.jvm
lazy val `metaconfig-hoconJS` = `metaconfig-hocon`.js

lazy val noPublish = Seq(
  publishArtifact := false,
  publish := {},
  publishLocal := {}
)
def customVersion = sys.props.get("metaconfig.version")

inScope(Global)(
  Seq(
    credentials ++= (for {
      username <- sys.env.get("SONATYPE_USERNAME")
      password <- sys.env.get("SONATYPE_PASSWORD")
    } yield
      Credentials(
        "Sonatype Nexus Repository Manager",
        "oss.sonatype.org",
        username,
        password)).toSeq,
    PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray())
  )
)
