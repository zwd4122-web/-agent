package com.chaoshan.travel.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaoshan.agents.ChaoshanTravelAgentFacade
import com.chaoshan.agents.model.TaskType

/**
 * 潮汕旅游 Multi-Agent 的 Android 交互入口。
 *
 * 重要说明：
 * - 这个 Activity 只做三件事：收集输入、调用 [ChaoshanTravelAgentFacade]、把结构化结果转成文字展示。
 * - 所有 Agent 的处理逻辑都在 :agents 模块里，界面不是能力的一部分：
 *   其他模块（或服务端、脚本、测试）可以直接调用同一个 facade，不需要依赖这个页面。
 * - 四个按钮分别对应「统一入口」与「各 Agent 独立入口」，便于在真机上直接对照验证。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var inputText: EditText
    private lateinit var resultText: TextView

    /** 统一调用入口，界面只是它的一个使用方。 */
    private val facade = ChaoshanTravelAgentFacade.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputText = findViewById(R.id.inputText)
        resultText = findViewById(R.id.resultText)

        findViewById<Button>(R.id.btnFull).setOnClickListener {
            render(ResultFormatter.format(facade.execute(inputText.text.toString())))
        }
        findViewById<Button>(R.id.btnProofread).setOnClickListener {
            render(ResultFormatter.formatProofread(facade.proofread(inputText.text.toString())))
        }
        findViewById<Button>(R.id.btnCategorize).setOnClickListener {
            render(ResultFormatter.formatCategorize(facade.categorize(inputText.text.toString())))
        }
        findViewById<Button>(R.id.btnActions).setOnClickListener {
            render(ResultFormatter.formatActions(facade.extractActions(inputText.text.toString())))
        }
        findViewById<Button>(R.id.btnSample).setOnClickListener {
            inputText.setText(getString(R.string.sample_text))
        }
    }

    private fun render(text: String) {
        resultText.text = text
    }
}
