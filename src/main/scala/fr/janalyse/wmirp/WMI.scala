package fr.janalyse.wmirp

import com.jacob.activeX.ActiveXComponent
import com.jacob.com.Dispatch
import com.jacob.com.EnumVariant
import com.jacob.com.Variant

trait WMI {
  val locator = new ActiveXComponent("WbemScripting.SWbemLocator")      //  SWbemLocator
  val swbemServices = locator.invoke("ConnectServer").toDispatch() 
  val wmiconnect = new ActiveXComponent(swbemServices)                  //  SWbemServices
  
  /* => OK but - we have to know what are the fields we want to get
  val query = "select * from Win32_PerfFormattedData_PerfOS_Processor"
  val vCollection = wmiconnect.invoke("ExecQuery", new Variant(query))  // SWbemObjectSet 
  val enumVariant = new EnumVariant(vCollection.toDispatch())
  
  while(enumVariant.hasMoreElements()) {
    val item = enumVariant.nextElement().toDispatch()
    val name = Dispatch.call(item, "Name").getString()
    println(s"-------------------- $name -------------------")
    
  }
  */
  
  /* => OK - Same as previous - we have to know what are the fields we want to get
  val obs = wmiconnect.invoke(
      "InstancesOf",
      new Variant("Win32_PerfFormattedData_PerfOS_Processor")) // => SWbemObjectSet
      
  val enumVariant = new EnumVariant(obs.toDispatch())  
  while(enumVariant.hasMoreElements()) {
    val item = enumVariant.nextElement().toDispatch()
    val name = Dispatch.call(item, "Name").getString()
    println(s"-------------------- $name -------------------")
  } 
  */

  
  /* NOK
  val obs = wmiconnect.invoke(
      "Get",
      new Variant("""\\.\ROOT\CIMV2:Win32_PerfFormattedData_PerfOS_Processor""")) // => SWbemObject
      
  val enumVariant = new EnumVariant(obs.toDispatch())  
  while(enumVariant.hasMoreElements()) {
    val item = enumVariant.nextElement().toDispatch()
    val name = Dispatch.call(item, "Name").getString()
    println(s"-------------------- $name -------------------")
  } 
  */

  val ob = wmiconnect.invoke(
      "Get",
      new Variant("WinMgmts:Win32_PerfFormattedData_PerfOS_Processor")) // => SWbemObject
      
  println(ob.toString())

}