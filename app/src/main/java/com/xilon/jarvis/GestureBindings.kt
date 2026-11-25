package com.xilon.jarvis

internal object GestureBindings {
 val keys=listOf("pinch","left","right","up","down")
 val labels=listOf("Ciupire","Palmă stânga","Palmă dreapta","Palmă sus","Palmă jos")
 val actions=listOf("Implicit","Dezactivat","Microfon","Înapoi","Acasă","Recente","următoarea melodie","melodia anterioară","crește volumul","scade volumul")
 fun resolve(value:String?):String=actions.firstOrNull{it==value} ?: "Implicit"
}
