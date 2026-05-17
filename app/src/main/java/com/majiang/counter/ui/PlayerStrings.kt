package com.majiang.counter.ui

import com.majiang.counter.domain.GamePhase
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit

/**
 * 面向玩家的界面文案（麻将术语，不含开发用语）。
 */
object PlayerStrings {
    const val FLOW_HINT = "请用底部导航「相机」打开预览并对准牌桌，再点「分析」开始打法推算。"
    const val OPEN_CAMERA = "打开相机"
    const val CLOSE_CAMERA = "关闭相机"
    const val START_ANALYSIS = "开始分析"
    const val ANALYZING = "打法推算中…"

    const val CAMERA_REQUIRED = "请先打开相机后再分析。"
    const val EXCHANGE_THREE_BLOCK = "换三张阶段请结束后再分析。"
    const val NO_FRAME = "尚未捕捉到画面，请稍候再试。"
    const val HAND_NOT_13 = "尚未识别齐本家 13 张手牌，请对准手牌区后重试。"
    /** 相机 HUD 提示：手牌裁切与画面橙框均以分析帧为准；请在设置中微调「本家手牌区」。 */
    const val HAND_RECOGNITION_HINT =
        "手牌识别以「设置」中的「本家手牌区」为准；橙虚线为手牌区，细红实线为四家舍牌区（与裁切共用同一坐标系）。"

    const val VISION_RIVER_PAUSED =
        "舍牌自动记录已暂停：牌墙张数对不上。请到设置检查画面区域，或重新对准牌桌。"
    const val RESUME_RIVER = "继续记录舍牌"

    const val SECTION_MY_HAND = "本家手牌"
    const val SECTION_DISCARDS = "四家舍牌"
    const val SECTION_ADVICE = "打法建议"
    const val SUGGEST_DISCARD = "建议打出"
    const val DISCARD_PRIORITY = "打出先后（由稳到险）"
    const val TENPAI = "听牌"
    const val WAITING_TENDENCY = "进张倾向"
    const val RON_RISK = "点炮风险（至少一家可荣和）"
    const val KONG_RISK = "点杠风险"
    const val NO_RESULT = "尚无建议；请用底部「相机」对准手牌后，再点「分析」。"
    const val WALL_REMAINING = "牌墙剩张"
    const val WALL_UNKNOWN = "—"
    const val DING_QUE = "定缺"
    const val DEALER = "庄家"
    const val WON_SEATS = "已胡"
    const val PHASE_LABEL = "阶段"

    const val SETTINGS_TITLE = "设置"
    const val SETTINGS_CALIB_HINT = "建议先在对局页用底部「相机」打开预览，再对照牌桌调整各区域。"
    const val SETTINGS_AUTO_WALL = "自动识别牌墙剩张"
    const val SETTINGS_AUTO_WALL_DESC = "对准牌桌中央「剩张」区域后自动更新。"
    const val SETTINGS_RIVER_ZONES = "四家舍牌区域"
    const val SETTINGS_EXTRA_ZONES = "其他画面区域"
    const val RESET_CURRENT = "恢复当前区域默认"
    const val RESET_ALL_RIVERS = "四家舍牌区域恢复默认"
    const val RESET_ALL = "全部区域恢复默认"
    const val EDGE_LEFT = "左缘"
    const val EDGE_TOP = "上缘"
    const val EDGE_RIGHT = "右缘"
    const val EDGE_BOTTOM = "下缘"

    const val TAB_PLAY = "对局"
    const val TAB_NAV_CAMERA = "相机"
    const val TAB_NAV_ANALYSIS = "分析"
    const val TAB_SETTINGS = "设置"

    /** 底部导航「相机」：打开/关闭预览（与 [TAB_NAV_CAMERA] 同项切换）。 */
    const val NAV_CAMERA_CD = "打开或关闭相机预览"

    /** 底部导航「分析」：开始打法推算。 */
    const val NAV_ANALYSIS_CD = "开始打法推算"

    const val CAMERA_PERMISSION_HINT = "请点底部「相机」，在系统弹窗中授予相机权限。"

    const val AUTH_LOGIN_TITLE = "登录"
    const val AUTH_LOGIN_SUBTITLE = "使用用户名和密码登录；添加新用户请由管理员在设置中操作。"
    const val AUTH_ADD_USER_TITLE = "添加用户"
    const val AUTH_ADD_USER_SUBTITLE = "为新用户设置用户名与密码（仅管理员可见此入口）。"
    const val AUTH_ADD_USER_BUTTON = "创建用户"
    const val AUTH_BACK = "返回"
    const val AUTH_CANCEL = "取消"
    const val AUTH_USERNAME = "用户名"
    const val AUTH_PASSWORD = "密码"
    const val AUTH_CONFIRM_PASSWORD = "确认密码"
    const val AUTH_LOGIN_BUTTON = "登录"
    const val AUTH_LOGGED_IN_AS = "当前用户"
    const val AUTH_LOGOUT = "退出登录"
    const val AUTH_ADD_USER_ENTRY = "添加用户"
    const val AUTH_CHANGE_PASSWORD = "修改密码"
    const val AUTH_OLD_PASSWORD = "当前密码"
    const val AUTH_NEW_PASSWORD = "新密码"
    const val AUTH_CONFIRM_NEW_PASSWORD = "确认新密码"
    const val AUTH_CHANGE_PASSWORD_CONFIRM = "保存新密码"

    fun phaseLabel(phase: GamePhase): String = when (phase) {
        GamePhase.PLAYING -> "对局中"
        GamePhase.EXCHANGE_THREE -> "换三张"
        GamePhase.CLAIM_WINDOW -> "碰杠响应"
    }

    fun dingQueLabel(suit: Suit?): String = when (suit) {
        null -> "未定"
        Suit.WAN -> "万"
        Suit.TIAO -> "条"
        Suit.TONG -> "筒"
    }

    fun seatShort(seat: Seat): String = when (seat) {
        Seat.EAST -> "东"
        Seat.SOUTH -> "南"
        Seat.WEST -> "西"
        Seat.NORTH -> "北"
    }
}
