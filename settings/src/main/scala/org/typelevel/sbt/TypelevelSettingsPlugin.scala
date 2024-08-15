/*
 * Copyright 2022 Typelevel
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

package org.typelevel.sbt

import sbt._, Keys._
import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.git
import org.typelevel.sbt.kernel.SemV
import org.typelevel.sbt.kernel.GitHelper

object TypelevelSettingsPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = TypelevelKernelPlugin && GitPlugin

  object autoImport {
    lazy val tlFatalWarnings =
      settingKey[Boolean]("Convert compiler warnings into errors (default: false)")
  }

  import autoImport._
  import TypelevelKernelPlugin.autoImport._

  override def globalSettings = Seq(
    tlFatalWarnings := false,
    Def.derive(scalaVersion := crossScalaVersions.value.last, default = true)
  )

  override def projectSettings = Seq(
    versionScheme := Some("early-semver"),
    pomIncludeRepository := { _ => false },
    libraryDependencies ++= {
      if (tlIsScala3.value)
        Nil
      else
        Seq(
          compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
          compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
        )
    },

    // Adapted from Rob Norris' post at https://tpolecat.github.io/2014/04/11/scalac-flags.html
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8", // yes, this is 2 args
      "-feature",
      "-unchecked"),
    scalacOptions ++= {
      scalaVersion.value match {
        case SemV(SemV(2, minor, _, _)) if minor < 13 =>
          Seq("-Yno-adapted-args", "-Ywarn-unused-import")
        case _ =>
          Seq.empty
      }
    },
    scalacOptions ++= {
      if (tlFatalWarnings.value)
        Seq("-Xfatal-warnings")
      else
        Seq.empty
    },
    scalacOptions ++= {
      val warningsNsc = Seq("-Xlint", "-Ywarn-dead-code")

      val warnings211 =
        Seq("-Ywarn-numeric-widen") // In 2.10 this produces a some strange spurious error

      val warnings212 = Seq("-Xlint:-unused,_")

      val removed213 = Set("-Xlint:-unused,_", "-Xlint")
      val warnings213 = Seq(
        "-Xlint:deprecation",
        "-Wunused:nowarn",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Wunused:implicits",
        "-Wunused:explicits",
        "-Wunused:imports",
        "-Wunused:locals",
        "-Wunused:params",
        "-Wunused:patvars",
        "-Wunused:privates",
        "-Wvalue-discard"
      )

      val warningsDotty = Seq()

      scalaVersion.value match {
        case SemV(SemV(3, _, _, _)) =>
          warningsDotty

        case SemV(SemV(2, minor, _, _)) if minor >= 13 =>
          (warnings211 ++ warnings212 ++ warnings213 ++ warningsNsc).filterNot(removed213)

        case SemV(SemV(2, minor, _, _)) if minor >= 12 =>
          warnings211 ++ warnings212 ++ warningsNsc

        case SemV(SemV(2, minor, _, _)) if minor >= 11 =>
          warnings211 ++ warningsNsc

        case _ => Seq.empty
      }
    },
    scalacOptions ++= {
      scalaVersion.value match {
        case SemV(SemV(2, 12, _, _)) =>
          Seq("-Ypartial-unification")

        case SemV(SemV(2, 11, Some(build), _)) if build >= 11 =>
          Seq("-Ypartial-unification")

        case _ =>
          Seq.empty
      }
    },
    scalacOptions ++= {
      val numCPUs = java.lang.Runtime.getRuntime.availableProcessors()
      val settings = Seq(s"-Ybackend-parallelism", scala.math.min(16, numCPUs).toString)

      scalaVersion.value match {
        case SemV(SemV(2, 12, Some(build), _)) if build >= 5 =>
          settings

        case SemV(SemV(2, 13, _, _)) =>
          settings

        case _ =>
          Seq.empty
      }
    },
    scalacOptions ++= {
      if (tlIsScala3.value && crossScalaVersions.value.forall(_.startsWith("3.")))
        Seq("-Ykind-projector:underscores")
      else if (tlIsScala3.value)
        Seq("-language:implicitConversions", "-Ykind-projector", "-source:3.0-migration")
      else
        Seq("-language:_")
    },
    Test / scalacOptions ++= {
      if (tlIsScala3.value)
        Seq()
      else
        Seq("-Yrangepos")
    },
    Compile / console / scalacOptions --= Seq(
      "-Xlint",
      "-Ywarn-unused-import",
      "-Wextra-implicit",
      "-Wunused:implicits",
      "-Wunused:explicits",
      "-Wunused:imports",
      "-Wunused:locals",
      "-Wunused:params",
      "-Wunused:patvars",
      "-Wunused:privates"
    ),
    Test / console / scalacOptions := (Compile / console / scalacOptions).value,
    Compile / doc / scalacOptions ++= {
      if (tlIsScala3.value)
        Seq("-sourcepath", (LocalRootProject / baseDirectory).value.getAbsolutePath)
      else {

        val tagOrHash =
          GitHelper.getTagOrHash(
            versionScheme.value,
            git.gitCurrentTags.value,
            git.gitHeadCommit.value)

        val infoOpt = scmInfo.value
        tagOrHash.toSeq flatMap { vh =>
          infoOpt.toSeq flatMap { info =>
            val path = s"${info.browseUrl}/blob/$vh€{FILE_PATH}.scala"
            Seq(
              "-doc-source-url",
              path,
              "-sourcepath",
              (LocalRootProject / baseDirectory).value.getAbsolutePath)
          }
        }
      }
    },
    javacOptions ++= Seq(
      "-encoding",
      "utf8",
      "-Xlint:all"
    ),
    javacOptions ++= {
      if (tlFatalWarnings.value)
        Seq("-Werror")
      else
        Seq.empty
    }
  )

}
