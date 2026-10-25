package com.ahu.ahutong.data.phonebook

data class PhoneBookEntry(val name: String, val numbers: List<String>)

/** 学校公开电话目录；页面与 AI 工具共用同一份数据。 */
object PhoneBookDirectory {
    val groups: Map<String, List<PhoneBookEntry>> = mapOf(
        "师生综合服务大厅" to listOf(
            PhoneBookEntry("咨询台", listOf("63861400")),
            PhoneBookEntry("财务处", listOf("63861322", "65107303")),
            PhoneBookEntry("研究生院", listOf("63861455", "65107332")),
            PhoneBookEntry("教务处", listOf("63861055", "65107135")),
            PhoneBookEntry("双创学院", listOf("65106065")),
            PhoneBookEntry("学生处（学生发展中心）", listOf("63861686", "65107232")),
            PhoneBookEntry("国际合作与交流处", listOf("63861848")),
            PhoneBookEntry("学校办公室", listOf("63861659", "65107020")),
            PhoneBookEntry("网络信息中心", listOf("63861855", "65107421")),
            PhoneBookEntry("保卫处", listOf("63861918")),
            PhoneBookEntry("人事处", listOf("63861922")),
            PhoneBookEntry("国有资产管理与实验室管理处", listOf("63861949", "65107310")),
            PhoneBookEntry("人文社会科学处", listOf("63861958")),
            PhoneBookEntry("科学技术处", listOf("63861983")),
            PhoneBookEntry("校团委", listOf("65107537")),
        ),
        "学生处（心理健康教育中心）" to listOf(
            PhoneBookEntry("学生思想教育科", listOf("63861900")),
            PhoneBookEntry("学生管理科", listOf("63861054")),
            PhoneBookEntry("学生资助管理中心", listOf("63861900")),
            PhoneBookEntry("学生就业指导中心", listOf("63861355", "63861383")),
            PhoneBookEntry("心理健康教育中心", listOf("63861590")),
        ),
        "校团委" to listOf(PhoneBookEntry("团委办公室", listOf("63861121", "63861196"))),
        "图书馆" to listOf(PhoneBookEntry("图书馆", listOf("63861109"))),
        "校医院（医保办）" to listOf(
            PhoneBookEntry("24 小时值班电话", listOf("63861120")),
            PhoneBookEntry("校医疗保障委员会办公室", listOf("63861715")),
        ),
        "后勤服务" to listOf(
            PhoneBookEntry("学校物业监管办公室", listOf("63861358")),
            PhoneBookEntry("餐饮监管办公室", listOf("63861398")),
        ),
        "报警电话" to listOf(
            PhoneBookEntry("保卫处（优先联系）", listOf("63861110", "65108229")),
            PhoneBookEntry("三里庵派出所", listOf("63636594")),
            PhoneBookEntry("芙蓉派出所", listOf("63822505")),
        ),
        "磬苑后勤" to listOf(
            PhoneBookEntry("校园报修电话", listOf("63861118")),
            PhoneBookEntry("服务监督电话", listOf("63861722")),
            PhoneBookEntry("北区 24 小时报修电话", listOf("62950090")),
            PhoneBookEntry("南区 24 小时报修电话", listOf("63861333")),
            PhoneBookEntry("桃园", listOf("63861034")),
            PhoneBookEntry("李园", listOf("63861037")),
            PhoneBookEntry("桔园", listOf("63861036")),
            PhoneBookEntry("枣园", listOf("63861218")),
            PhoneBookEntry("榴园", listOf("63861217")),
            PhoneBookEntry("杏园", listOf("63861219")),
            PhoneBookEntry("蕙8", listOf("63861654")),
            PhoneBookEntry("蕙9", listOf("63861617")),
            PhoneBookEntry("蕙10", listOf("63861697")),
            PhoneBookEntry("松园", listOf("63861160")),
            PhoneBookEntry("竹园", listOf("63861115")),
            PhoneBookEntry("梅园", listOf("63861113")),
            PhoneBookEntry("桂园", listOf("63861097")),
            PhoneBookEntry("枫园", listOf("63861096")),
            PhoneBookEntry("槐园", listOf("63861081")),
            PhoneBookEntry("留学生公寓", listOf("63861096")),
        ),
        "龙河后勤" to listOf(
            PhoneBookEntry("宿舍管理办公室", listOf("65107014")),
            PhoneBookEntry("校园报修电话", listOf("63861118")),
            PhoneBookEntry("服务监督电话", listOf("63861722")),
            PhoneBookEntry("206楼", listOf("65107064")),
            PhoneBookEntry("207-208楼", listOf("65108150")),
            PhoneBookEntry("209楼", listOf("65107940")),
            PhoneBookEntry("301楼", listOf("65108004")),
            PhoneBookEntry("304-306楼", listOf("65108004")),
        ),
    )
}
