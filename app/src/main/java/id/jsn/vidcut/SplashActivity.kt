package id.jsn.vidcut

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Optimized animated splash:
 * - Hardware-accelerated logo fade + overshoot scale
 * - Smooth decelerated progress bar
 * - Label fade-in staggered
 * - AnimatorSet for coordinated timing
 * - Proper cancel on destroy to avoid leaks
 */
class SplashActivity : AppCompatActivity() {

    private var animatorSet: AnimatorSet? = null
    private var progressAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.vidcut_splash)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.78f
            scaleY = 0.78f
            // Hardware layer for smoother scale/alpha
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        root.addView(
            logo,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
        )

        val loading = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            isIndeterminate = false
            progressDrawable = ContextCompat.getDrawable(
                this@SplashActivity,
                R.drawable.vidcut_splash_progress
            )
        }
        root.addView(
            loading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                10
            ).apply {
                topMargin = 28
                leftMargin = 56
                rightMargin = 56
            }
        )

        val label = TextView(this).apply {
            text = "VidCuT"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            alpha = 0f
            letterSpacing = 0.12f
        }
        root.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18 }
        )

        setContentView(root)

        val easeOut = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        val overshoot = OvershootInterpolator(1.15f)

        val fadeIn = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
            duration = 480
            interpolator = easeOut
        }
        val scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.78f, 1f).apply {
            duration = 720
            interpolator = overshoot
        }
        val scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.78f, 1f).apply {
            duration = 720
            interpolator = overshoot
        }
        val labelFade = ObjectAnimator.ofFloat(label, View.ALPHA, 0f, 1f).apply {
            startDelay = 280
            duration = 420
            interpolator = DecelerateInterpolator()
        }

        animatorSet = AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY, labelFade)
            start()
        }

        progressAnimator = ValueAnimator.ofInt(0, 1000).apply {
            duration = 1550
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { a ->
                loading.progress = a.animatedValue as Int
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isFinishing) startMain()
                }
            })
            start()
        }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        animatorSet?.cancel()
        progressAnimator?.cancel()
        animatorSet = null
        progressAnimator = null
        super.onDestroy()
    }
}
