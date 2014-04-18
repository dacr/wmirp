package fr.janalyse.wmirp

import com.jacob.activeX.ActiveXComponent
import com.jacob.com.Dispatch
import com.jacob.com.EnumVariant
import com.jacob.com.Variant

trait WMI {
  val wmi = new ActiveXComponent("WbemScripting.SWbemLocator")
  val remote: Variant = wmi.invoke("ConnectServer")
  val wmiconnect = new ActiveXComponent(remote.toDispatch())
  val query = "select * from Win32_PerfFormattedData_PerfOS_Processor"
  val vCollection = wmiconnect.invoke("ExecQuery", new Variant(query))

  val enumVariant = new EnumVariant(vCollection.toDispatch())
  
  while(enumVariant.hasMoreElements()) {
    val item = enumVariant.nextElement().toDispatch()
    val name = Dispatch.call(item, "Name").getString()
    println(name)
  }
}