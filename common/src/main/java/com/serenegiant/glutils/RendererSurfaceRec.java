package com.serenegiant.glutils;

import android.annotation.TargetApi;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.SystemClock;

import com.serenegiant.utils.BuildCheck;

/**
 * Created by saki on 2016/10/09.
 * 同じ内容のクラスだったからEffectRendererHolder/RendererHolderのインナークラスを外に出した
 */
class RendererSurfaceRec {

	/**
	 * ファクトリーメソッド
	 * @param egl
	 * @param surface
	 * @param maxFps 0以下なら最大描画フレームレート制限なし, あまり正確じゃない
	 * @return
	 */
	static RendererSurfaceRec newInstance(final EGLBase egl, final Object surface, final int maxFps) {
		return (maxFps > 0)
			? (BuildCheck.isJellyBeanMr1()
				? new RendererSurfaceRecHasWaitAPI17(egl, surface, maxFps)	// API>=17
				: new RendererSurfaceRecHasWait(egl, surface, maxFps))		// API<17
			: new RendererSurfaceRec(egl, surface);	// no limitation of maxFps
	}

	/** 元々の分配描画用Surface */
	private Object mSurface;
	/** 分配描画用Surfaceを元に生成したOpenGL|ESで描画する為のEglSurface */
	private EGLBase.IEglSurface mTargetSurface;
	final float[] mMvpMatrix = new float[16];
	protected volatile boolean mEnable = true;

	/**
	 * コンストラクタ, ファクトリーメソッドの使用を強制するためprivate
	 * @param egl
	 * @param surface
	 */
	private RendererSurfaceRec(final EGLBase egl, final Object surface) {
		mSurface = surface;
		mTargetSurface = egl.createFromSurface(surface);
		Matrix.setIdentityM(mMvpMatrix, 0);
	}

	/**
	 * 生成したEglSurfaceを破棄する
	 */
	public void release() {
		if (mTargetSurface != null) {
			mTargetSurface.release();
			mTargetSurface = null;
		}
		mSurface = null;
	}
	
	/**
	 * Surfaceが有効かどうかを取得する
	 * @return
	 */
	public boolean isValid() {
		return (mTargetSurface != null) && mTargetSurface.isValid();
	}
	
	/**
	 * Surfaceへの描画が有効かどうかを取得する
	 * @return
	 */
	public boolean isEnabled() {
		return mEnable;
	}
	
	/**
	 * Surfaceへの描画を一時的に有効/無効にする
	 * @param enable
	 */
	public void setEnabled(final boolean enable) {
		mEnable = enable;
	}
	
	public boolean canDraw() {
		return mEnable;
	}

	public void draw(final GLDrawer2D drawer, final int textId, final float[] texMatrix) {
		mTargetSurface.makeCurrent();
		// 本来は映像が全面に描画されるので#glClearでクリアする必要はないけどハングアップする機種があるのでクリアしとく
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		drawer.setMvpMatrix(mMvpMatrix, 0);
		drawer.draw(textId, texMatrix, 0);
		mTargetSurface.swap();
	}
	
	/**
	 * 指定した色で全面を塗りつぶす
	 * @param color
	 */
	public void clear(final int color) {
		mTargetSurface.makeCurrent();
		GLES20.glClearColor(
			((color & 0x00ff0000) >>> 16) / 255.0f,	// R
			((color & 0x0000ff00) >>>  8) / 255.0f,	// G
			((color & 0x000000ff)) / 255.0f,		// B
			((color & 0xff000000) >>> 24) / 255.0f	// A
		);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		mTargetSurface.swap();
	}
	
	/**
	 * #drawの代わりにOpenGL|ES2を使って自前で描画する場合は
	 * #makeCurrentでレンダリングコンテキストを切り替えてから
	 * 描画後#swapを呼ぶ
	 */
	public void makeCurrent() {
		mTargetSurface.makeCurrent();
	}

	/**
	 * #drawの代わりにOpenGL|ES2を使って自前で描画する場合は
	 * #makeCurrentでレンダリングコンテキストを切り替えてから
	 * 描画後#swapを呼ぶ
	 */
	public void swap() {
		mTargetSurface.swap();
	}

	private static class RendererSurfaceRecHasWait extends RendererSurfaceRec {
		private long mNextDraw;
		private final long mIntervalsNs;

		/**
		 * コンストラクタ, ファクトリーメソッドの使用を強制するためprivate
		 * @param egl
		 * @param surface
		 * @param maxFps 正数
		 */
		private RendererSurfaceRecHasWait(final EGLBase egl, final Object surface, final int maxFps) {
			super(egl, surface);
			mIntervalsNs = 1000000000L / maxFps;
			mNextDraw = System.nanoTime() + mIntervalsNs;
		}

		@Override
		public boolean canDraw() {
			return mEnable && (System.nanoTime() - mNextDraw > 0);
		}

		@Override
		public void draw(final GLDrawer2D drawer, final int textId, final float[] texMatrix) {
			mNextDraw = System.nanoTime() + mIntervalsNs;
			super.draw(drawer, textId, texMatrix);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private static class RendererSurfaceRecHasWaitAPI17 extends RendererSurfaceRec {
		private long mNextDraw;
		private final long mIntervalsNs;

		/**
		 * コンストラクタ, ファクトリーメソッドの使用を強制するためprivate
		 * @param egl
		 * @param surface
		 * @param maxFps 正数
		 */
		private RendererSurfaceRecHasWaitAPI17(final EGLBase egl, final Object surface, final int maxFps) {
			super(egl, surface);
			mIntervalsNs = 1000000000L / maxFps;
			mNextDraw = SystemClock.elapsedRealtimeNanos() + mIntervalsNs;
		}

		@Override
		public boolean canDraw() {
			return mEnable && (SystemClock.elapsedRealtimeNanos() - mNextDraw > 0);
		}

		@Override
		public void draw(final GLDrawer2D drawer, final int textId, final float[] texMatrix) {
			mNextDraw = SystemClock.elapsedRealtimeNanos() + mIntervalsNs;
			super.draw(drawer, textId, texMatrix);
		}
	}

}
