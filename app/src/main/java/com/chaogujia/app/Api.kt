package com.chaogujia.app

import org.json.JSONObject

/**
 * 后端接口封装。
 * 后端统一返回：{ "code":0, "msg":"...", "data": {...} }
 *  code==0 表示成功。
 */

/** 账户信息（account.php） */
data class AccountInfo(
    val total: Double = 0.0,
    val initial: Double = 0.0
)

/** 会员套餐（member.php 的 plans[]） */
data class MemberPlan(
    val name: String = "",
    val desc: String = "",
    val price: Double = 0.0,
    val unit: String = "",
    val hot: Boolean = false,
    val key: String = ""
)

/** 会员信息（member.php） */
data class MemberInfo(
    val isMember: Boolean = false,
    val levelName: String = "普通用户",
    val expireText: String = "",
    val plans: List<MemberPlan> = emptyList()
)
object Api {

    private fun base(url: String) = Config.BASE_URL + url

    private fun jsonOf(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in pairs) o.put(k, v)
        return o.toString()
    }

    /** 登录结果：成功返回 token+username，失败返回错误信息 */
    data class AuthResult(val ok: Boolean, val token: String = "", val username: String = "", val msg: String = "")

    /** 登录 */
    fun login(username: String, password: String): AuthResult {
        return try {
            val raw = Net.postJson(
                base("login.php"),
                jsonOf("username" to username, "password" to password),
                token = null,
                withTokenParam = false
            )
            val o = JSONObject(raw)
            if (o.optInt("code", -1) == 0) {
                val d = o.optJSONObject("data")
                AuthResult(
                    ok = true,
                    token = d?.optString("token", "") ?: "",
                    username = d?.optString("username", username) ?: username
                )
            } else {
                AuthResult(ok = false, msg = o.optString("msg", "登录失败"))
            }
        } catch (e: Exception) {
            AuthResult(ok = false, msg = e.message ?: "网络异常")
        }
    }

    /** 注册（成功后返回 msg，不自动登录，由调用方决定） */
    fun register(username: String, password: String): AuthResult {
        return try {
            val raw = Net.postJson(
                base("register.php"),
                jsonOf("username" to username, "password" to password),
                token = null,
                withTokenParam = false
            )
            val o = JSONObject(raw)
            if (o.optInt("code", -1) == 0) {
                val d = o.optJSONObject("data")
                AuthResult(
                    ok = true,
                    token = d?.optString("token", "") ?: "",
                    username = d?.optString("username", username) ?: username,
                    msg = o.optString("msg", "注册成功")
                )
            } else {
                AuthResult(ok = false, msg = o.optString("msg", "注册失败"))
            }
        } catch (e: Exception) {
            AuthResult(ok = false, msg = e.message ?: "网络异常")
        }
    }


    /** 从 {code,msg,data} 里取出 data 对象 */
    private fun dataOf(raw: String): JSONObject? {
        return try {
            val o = JSONObject(raw)
            if (o.optInt("code", -1) == 0) o.optJSONObject("data") else null
        } catch (e: Exception) {
            null
        }
    }

    /** 拉取账户信息 */
    fun account(token: String?): AccountInfo {
        val raw = Net.get(base("account.php"), token)
        val d = dataOf(raw) ?: return AccountInfo()
        return AccountInfo(
            total = d.optDouble("total", 0.0),
            initial = d.optDouble("initial", 0.0)
        )
    }

    /** 拉取会员信息 */
    fun member(token: String?): MemberInfo {
        val raw = Net.get(base("member.php"), token)
        val d = dataOf(raw) ?: return MemberInfo()
        val arr = d.optJSONArray("plans")
        val plans = ArrayList<MemberPlan>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                plans.add(
                    MemberPlan(
                        name = p.optString("name", ""),
                        desc = p.optString("desc", ""),
                        price = p.optDouble("price", 0.0),
                        unit = p.optString("unit", ""),
                        hot = p.optBoolean("hot", false),
                        key = p.optString("key", "")
                    )
                )
            }
        }
        return MemberInfo(
            isMember = d.optBoolean("isMember", false),
            levelName = d.optString("levelName", "普通用户"),
            expireText = d.optString("expireText", ""),
            plans = plans
        )
    }
}