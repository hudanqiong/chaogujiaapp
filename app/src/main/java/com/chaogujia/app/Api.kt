package com.chaogujia.app

import org.json.JSONObject

/**
 * 后端接口封装。
 * 后端统一返回：{ "code":0, "msg":"...", "data": {...} }
 *  code==0 表示成功。
 */

/** 账户信息（account.php） */
data class AccountInfo(
    val cash: Double = 0.0,
    val marketValue: Double = 0.0,
    val total: Double = 0.0,
    val floatPn: Double = 0.0,
    val totalPn: Double = 0.0,
    val totalPnPct: Double = 0.0,
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
    val expireAt: String = "",
    val plans: List<MemberPlan> = emptyList()
)

/** 积分信息（point.php） */
data class PointInfo(
    val points: Int = 0
)

/** 积分流水（point.php 的 logs[]） */
data class PointLog(
    val desc: String = "",
    val points: Int = 0,
    val time: String = ""
)

/** 邀请信息（invite.php） */
data class InviteInfo(
    val code: String = "",
    val invitedCount: Int = 0,
    val totalReward: Int = 0,
    val users: List<InvitedUser> = emptyList()
)

/** 被邀请用户 */
data class InvitedUser(
    val username: String = "",
    val reward: Int = 0,
    val time: String = ""
)

/** 券商 */
data class Broker(
    val id: String = "",
    val name: String = ""
)

/** 我的券商账户 */
data class MyBroker(
    val broker: String = "",
    val account: String = ""
)

/** 自选分组（watch_group.php） */
data class WatchGroup(
    val id: Int = 0,
    val name: String = "",
    val sort: Int = 0
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

    /** 通用结果 */
    data class Res(val ok: Boolean, val msg: String = "")

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

    /** 注册 */
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

    /** 通用请求：返回 (ok, msg) */
    private fun post(url: String, token: String?, vararg pairs: Pair<String, Any?>): Res {
        return try {
            val raw = Net.postJson(base(url), jsonOf(*pairs), token)
            val o = JSONObject(raw)
            Res(o.optInt("code", -1) == 0, o.optString("msg", ""))
        } catch (e: Exception) {
            Res(false, e.message ?: "网络异常")
        }
    }

    /** 拉取账户信息 */
    fun account(token: String?): AccountInfo {
        val raw = Net.get(base("account.php"), token)
        val d = dataOf(raw) ?: return AccountInfo()
        return AccountInfo(
            cash = d.optDouble("cash", 0.0),
            marketValue = d.optDouble("marketValue", 0.0),
            total = d.optDouble("total", 0.0),
            floatPn = d.optDouble("floatPn", 0.0),
            totalPn = d.optDouble("totalPn", 0.0),
            totalPnPct = d.optDouble("totalPnPct", 0.0),
            initial = d.optDouble("initial", 0.0)
        )
    }

    /** 设置初始资金 */
    fun setInitial(token: String?, amount: Double): Res =
        post("account.php", token, "action" to "set_initial", "initial" to amount)

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
            expireAt = d.optString("expireAt", ""),
            plans = plans
        )
    }

    /** 积分兑换会员 */
    fun exchangeMember(token: String?, plan: String): Res =
        post("exchange_member.php", token, "plan" to plan)

    /** 拉取积分信息 */
    fun point(token: String?): PointInfo {
        val raw = Net.get(base("point.php"), token)
        val d = dataOf(raw) ?: return PointInfo()
        return PointInfo(points = d.optInt("points", 0))
    }

    /** 拉取积分流水 */
    fun pointLogs(token: String?): List<PointLog> {
        val raw = Net.get(base("point.php?action=logs"), token)
        val d = dataOf(raw) ?: return emptyList()
        val arr = d.optJSONArray("logs") ?: return emptyList()
        val list = ArrayList<PointLog>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            list.add(
                PointLog(
                    desc = p.optString("desc", ""),
                    points = p.optInt("points", 0),
                    time = p.optString("time", "")
                )
            )
        }
        return list
    }

    /** 拉取邀请信息 */
    fun invite(token: String?): InviteInfo {
        val raw = Net.get(base("invite.php"), token)
        val d = dataOf(raw) ?: return InviteInfo()
        val arr = d.optJSONArray("users")
        val users = ArrayList<InvitedUser>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                users.add(
                    InvitedUser(
                        username = p.optString("username", ""),
                        reward = p.optInt("reward", 0),
                        time = p.optString("time", "")
                    )
                )
            }
        }
        return InviteInfo(
            code = d.optString("code", ""),
            invitedCount = d.optInt("invitedCount", 0),
            totalReward = d.optInt("totalReward", 0),
            users = users
        )
    }

    /** 内置券商列表 */
    val BROKERS = listOf(
        Broker("htzq", "华泰证券"),
        Broker("zszq", "招商证券"),
        Broker("gjzq", "国泰君安"),
        Broker("zxzq", "中信证券"),
        Broker("gyzq", "国元证券"),
        Broker("dfzq", "东方证券"),
        Broker("xyzq", "兴业证券"),
        Broker("gfzq", "广发证券"),
        Broker("pa", "平安证券"),
        Broker("cjzq", "长江证券")
    )

    /** 拉取我的券商账户 */
    fun myBroker(token: String?): MyBroker {
        val raw = Net.get(base("broker.php"), token)
        val d = dataOf(raw) ?: return MyBroker()
        return MyBroker(
            broker = d.optString("broker", ""),
            account = d.optString("account", "")
        )
    }

    /** 保存券商账户 */
    fun saveBroker(token: String?, broker: String, account: String): Res =
        post("broker.php", token, "broker" to broker, "account" to account)

    /** 自选分组列表 */
    fun watchGroups(token: String?): List<WatchGroup> {
        val raw = Net.postJson(base("watch_group.php"), jsonOf("action" to "list"), token)
        val d = dataOf(raw) ?: return emptyList()
        val arr = d.optJSONArray("groups") ?: return emptyList()
        val list = ArrayList<WatchGroup>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            list.add(
                WatchGroup(
                    id = p.optInt("id", 0),
                    name = p.optString("name", ""),
                    sort = p.optInt("sort", 0)
                )
            )
        }
        return list
    }

    /** 新增分组 */
    fun addGroup(token: String?, name: String): Res =
        post("watch_group.php", token, "action" to "add", "name" to name)

    /** 分组改名 */
    fun renameGroup(token: String?, gid: Int, name: String): Res =
        post("watch_group.php", token, "action" to "rename", "gid" to gid, "name" to name)

    /** 删除分组 */
    fun delGroup(token: String?, gid: Int): Res =
        post("watch_group.php", token, "action" to "del", "gid" to gid)

    /** 加自选（到指定分组） */
    fun addWatch(token: String?, code: String, gid: Int): Res =
        post("watchlist.php", token, "action" to "add", "code" to code, "gid" to gid)

    /** 删除自选 */
    fun delWatch(token: String?, code: String, gid: Int): Res =
        post("watchlist.php", token, "action" to "del", "code" to code, "gid" to gid)

    /** 修改密码 */
    fun changePwd(token: String?, oldPwd: String, newPwd: String): Res =
        post("user.php", token, "action" to "change_pwd", "old" to oldPwd, "new" to newPwd)

    /** 退出登录（服务端可无视，前端清理本地即可） */
    fun logout(token: String?): Res = post("logout.php", token)
}
