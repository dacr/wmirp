package fr.janalyse.wmirp

import com.jacob.activeX.ActiveXComponent
import com.jacob.com.Dispatch
import com.jacob.com.EnumVariant
import com.jacob.com.Variant

case class ComClass(path: String) {
  val Array(location, name) = path.split(":", 2)
  def attributes(implicit wmi: WMI) = {
    wmi.getClassAttributes(this)
  }
}

object ComClass {
  implicit def string2ComClass(path: String): ComClass = ComClass(path)
}

trait WMI {
  val swbemlocator = new ActiveXComponent("WbemScripting.SWbemLocator")
  val swbemservices = new ActiveXComponent(swbemlocator.invoke("ConnectServer").toDispatch())

  def getClasses(): List[ComClass] = {
    val classes = swbemservices.invoke("SubclassesOf")
    val enumVariant = new EnumVariant(classes.toDispatch())
    var result = List.empty[ComClass]
    while (enumVariant.hasMoreElements()) {
      val item = enumVariant.nextElement().toDispatch()
      val pathd = Dispatch.call(item, "Path_").toDispatch()
      val path = Dispatch.get(pathd, "Path").toString
      result = ComClass(path) :: result
    }
    result
  }

  def getPerfClasses() = getClasses.filter(_.name contains "PerfFormatted")

  def getClassAttributes(comClass: ComClass): List[String] = {
    val tmp = swbemservices.invoke("Get",new Variant(comClass.path))
    val ob = tmp.toDispatch()
    val props = Dispatch.call(ob, "Properties_").toDispatch
    val enumProps = new EnumVariant(props)
    var result = List.empty[String]
    while(enumProps.hasMoreElements()) {
      val item = enumProps.nextElement.toDispatch()
      val key = Dispatch.get(item, "Name")
      val value = Dispatch.get(item, "Value")
      result = key.toString::result
    }
    result
  }

  def close() {
    swbemservices.safeRelease()
    swbemlocator.safeRelease()
  }
}