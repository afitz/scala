/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/license.html
 */
package scala.tools.nsc.classpath

import scala.reflect.io.AbstractFile
import java.io.File
import java.io.FileFilter
import scala.reflect.io.PlainFile
import FileUtils.FileOps

case class DirectoryFlatClasspath(dir: File) extends FlatClasspath {
  import FlatClasspath.RootPackage
  import DirectoryFlatClasspath.ClassfileEntryImpl
  assert(dir != null)
  
  private def getDirectory(forPackage: String): Option[File] = {
    if (forPackage == RootPackage) {
      Some(dir)
    } else {
      val packageDirName = FileUtils.dirPath(forPackage)
      val packageDir = new File(dir, packageDirName)
      if (packageDir.exists && packageDir.isDirectory) {
        Some(packageDir)
      } else None
    }
  }

  private object packageDirectoryFileFilter extends FileFilter {
    def accept(pathname: File): Boolean = pathname.isPackage
  }

  override def packages(inPackage: String): Seq[PackageEntry] = {
    val dirForPackage = getDirectory(inPackage)
    val nestedDirs: Array[File] = dirForPackage match {
      case None => Array.empty
      case Some(directory) => directory.listFiles(packageDirectoryFileFilter)
    }
    val prefix = if (inPackage == RootPackage) "" else inPackage + "."
    val entries = nestedDirs map { file =>
      PackageEntryImpl(prefix + file.getName)
    }
    entries
  }
  
  private object classFileFileFilter extends FileFilter {
    def accept(pathname: File): Boolean = pathname.isClass
  }
  
  override def classes(inPackage: String): Seq[ClassfileEntry] = {
    val dirForPackage = getDirectory(inPackage)
    val classfiles: Array[File] = dirForPackage match {
      case None => Array.empty
      case Some(directory) => directory.listFiles(classFileFileFilter)
    }
    val entries = classfiles map { file =>
      val wrappedFile = new scala.reflect.io.File(file)
      ClassfileEntryImpl(new PlainFile(wrappedFile))
    }
    entries
  }

  override def list(inPackage: String): (Seq[PackageEntry], Seq[ClassfileEntry]) = {
    val dirForPackage = getDirectory(inPackage)
    val files: Array[File] = dirForPackage match {
      case None => Array.empty
      case Some(directory) => directory.listFiles()
    }
    val packagePrefix = if (inPackage == RootPackage) "" else inPackage + "."
    val packageBuf = collection.mutable.ArrayBuffer.empty[PackageEntry]
    val classfileBuf = collection.mutable.ArrayBuffer.empty[ClassfileEntry]
    for (file <- files) {
      if (file.isPackage) {
        val pkgEntry = PackageEntryImpl(packagePrefix + file.getName)
        packageBuf += pkgEntry
      } else if (file.isClass) {
        val wrappedClassFile = new scala.reflect.io.File(file)
        val abstractClassFile = new PlainFile(wrappedClassFile)
        val classfileEntry = ClassfileEntryImpl(abstractClassFile)
        classfileBuf += classfileEntry
      }
    }
    (packageBuf, classfileBuf)
  }

	override def findClassFile(className: String): Option[AbstractFile] = {
		val classfile = new File(dir, s"$className.class")
		if (classfile.exists) {
			val wrappedClassFile = new scala.reflect.io.File(classfile)
			val abstractClassFile = new PlainFile(wrappedClassFile)
			Some(abstractClassFile)
		} else None
	}
}

object DirectoryFlatClasspath {

  private case class ClassfileEntryImpl(file: AbstractFile) extends ClassfileEntry {
    override def name = {
      val className = FileUtils.stripClassExtension(file.name)
      className
    }
  }
}
