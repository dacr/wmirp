package fr.janalyse.wmirp

import com.jacob.activeX.ActiveXComponent
import com.jacob.com.Dispatch
import com.jacob.com.EnumVariant
import com.jacob.com.Variant
import com.typesafe.scalalogging.slf4j.Logging

case class InstanceName(val id: Option[String]) {
  def isEmpty = id.isEmpty
  def isDefined = id.isDefined
}

object InstanceName {
  def apply(desc: String): InstanceName = {
    desc match {
      case "" | "null" | "\"null\""|null => new InstanceName(None)
      case n => new InstanceName(Some(n))
    }
  }
  implicit def string2InstanceName(desc:String):InstanceName = 
    apply(desc)
}

case class ComClass(desc: String) {
  val defaultBasePath = """\\.\root\cimv2"""
  val (path, location, name) = {
    val RE1 = "([^:]+:(.*))".r
    desc match {
      case RE1(loc, name) => (desc, loc, name)
      case x => (defaultBasePath + ":" + desc, defaultBasePath, desc)
    }
  }

  def attributes(implicit wmi: WMI) = {
    wmi.getClassAttributes(this)
  }
  def instances(implicit wmi: WMI): List[ComInstance] = {
    wmi.getInstances(this)
  }
  def instancesNames(implicit wmi: WMI): List[String] = {
    wmi.getInstancesNames(this)
  }
  def get(name: String)(implicit wmi: WMI): Option[ComInstance] = {
    wmi.getInstance(this, InstanceName(name))
  }
}

object ComClass {
  implicit def string2ComClass(path: String): ComClass = ComClass(path)
}

case class ComInstance(comClass: ComClass, name: InstanceName) {
  def entries(implicit wmi: WMI): Map[String, Variant] =
    wmi.getAttributesValues(this)
}

trait WMI extends Logging {
  val swbemlocator = new ActiveXComponent("WbemScripting.SWbemLocator")
  val swbemservices = new ActiveXComponent(swbemlocator.invoke("ConnectServer").toDispatch())

  def useDispatch[T](from: Variant)(todo: Dispatch => T): T = {
    val dispatch = from.toDispatch()
    try {
      todo(dispatch)
    } finally {
      dispatch.safeRelease()
    }
  }

  def getClasses(): List[ComClass] = {
    val classes = swbemservices.invoke("SubclassesOf")
    var result = List.empty[ComClass]
    useDispatch(classes) { classesDispatch =>
      val enumVariant = new EnumVariant(classesDispatch)
      while (enumVariant.hasMoreElements()) {
        useDispatch(enumVariant.nextElement()) { itemDispatch =>
          useDispatch(Dispatch.call(itemDispatch, "Path_")) { pathDispatch =>
            val path = Dispatch.get(pathDispatch, "Path").toString
            result = ComClass(path) :: result
          }
        }
      }
    }
    result
  }

  def getPerfClasses() = getClasses.filter(_.name contains "PerfFormatted")

  def getClassAttributes(comClass: ComClass): List[String] = {
    var result = List.empty[String]
    useDispatch(swbemservices.invoke("Get", new Variant(comClass.name))) { comClassDispatch =>
      useDispatch(Dispatch.call(comClassDispatch, "Properties_")) { propsDispatch =>
        val enumProps = new EnumVariant(propsDispatch)
        while (enumProps.hasMoreElements()) {
          useDispatch(enumProps.nextElement) { itemDispatch =>
            val key = Dispatch.get(itemDispatch, "Name")
            //val value = Dispatch.get(item, "Value")
            result = key.toString :: result
          }
        }
      }
    }
    result
  }

  def getInstancesNames(comClass: ComClass) = {
    var result = List.empty[String]
    useDispatch(swbemservices.invoke("InstancesOf", new Variant(comClass.name))) { dispatch =>
      val enumVariant = new EnumVariant(dispatch)
      while (enumVariant.hasMoreElements()) {
        useDispatch(enumVariant.nextElement()) { itemDispatch =>
          val name = Dispatch.call(itemDispatch, "Name")
          result = name.getString() :: result
        }
      }
    }
    result
  }

  def getInstances(comClass: ComClass): List[ComInstance] = {
    var result = List.empty[ComInstance]
    useDispatch(swbemservices.invoke("InstancesOf", new Variant(comClass.name))) { dispatch =>
      val enumVariant = new EnumVariant(dispatch)
      while (enumVariant.hasMoreElements()) {
        useDispatch(enumVariant.nextElement()) { itemDispatch =>
          val name = InstanceName(Dispatch.call(itemDispatch, "Name").getString)
          val instance = ComInstance(comClass, name)
          result = instance :: result
        }
      }
    }
    result
  }

  def getInstance(comClass: ComClass, name: InstanceName = InstanceName(None)): Option[ComInstance] = {
    val id = if (name.isDefined) comClass.path + ".Name=\"" + name.id.get + "\""
    else comClass.path
    useDispatch(swbemservices.invoke("Get", new Variant(id))) { instanceDispatch =>
      try {
        val foundName = InstanceName(Dispatch.call(instanceDispatch, "Name").getString)

        foundName.id match {
          case None if name.isDefined => None
          case None if name.isEmpty => Some(ComInstance(comClass, name))
          case Some(id) if name.isDefined && name.id.get == id => Some(ComInstance(comClass, name))
          case _ => None
        }
      } catch {
        case _: Exception => None
      }
    }
  }

  def getAttributesValues(instance: ComInstance): Map[String, Variant] = {
    var result = Map.empty[String, Variant]
    val id = if (instance.name.isDefined) instance.comClass.path + ".Name=\"" + instance.name.id.get + "\""
    else instance.comClass.path
    try {
      useDispatch(swbemservices.invoke("Get", new Variant(id))) { instanceDispatch =>
        for {
          attr <- instance.comClass.attributes(this)
          value = Dispatch.get(instanceDispatch, attr)
        } {
          result += attr -> value
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Exception in Get operation with $id", e)
        throw e
    }
    result
  }

  def getAttributeValue(instance: ComInstance, name: String): Variant = {
    ???
  }

  def close() {
    swbemservices.safeRelease()
    swbemlocator.safeRelease()
  }
}