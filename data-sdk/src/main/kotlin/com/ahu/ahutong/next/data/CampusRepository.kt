package com.ahu.ahutong.next.data

interface CampusRepository {
    suspend fun schedule(days: Int = 7): List<CourseOccurrence>
    suspend fun assignments(days: Int = 14): List<Assignment>
    suspend fun grades(term: String? = null): List<Grade>
    suspend fun balance(): Balance
    suspend fun bills(): BillSummary
    suspend fun exams(): List<Exam>
    suspend fun electricity(): Electricity

    /** 当前选定的电费房间展示名；未设置时为 null。 */
    fun electricityRoomLabel(): String?

    /**
     * 校园卡充值。bank 只接受 "agricultural" 或 "cmb"。
     * 这是真实扣款操作，调用方必须先取得用户确认。
     */
    suspend fun rechargeCard(amountYuan: String, bank: String): RechargeReceipt

    /** 宿舍电费充值，房间取自设置里保存的选择。password 为校园卡 6 位支付密码。 */
    suspend fun rechargeElectricity(amountYuan: String, password: String): RechargeReceipt

    /** 浴室缴费。bathroom 取「竹园/龙河」或「桔园/蕙园」，phone 为 11 位手机号。 */
    suspend fun rechargeBathroom(
        bathroom: String,
        phone: String,
        amountYuan: String,
        password: String,
    ): RechargeReceipt

    /** 网费充值。password 为校园卡 6 位支付密码。 */
    suspend fun rechargeNetwork(amountYuan: String, password: String): RechargeReceipt

    suspend fun addCalendarEvent(
        title: String,
        start: String,
        end: String,
        reminderMinutes: Int,
    ): CalendarEvent
}
