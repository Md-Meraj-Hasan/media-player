<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <!-- 1. VIDEO SURFACE -->
    <!-- Agar aap ExoPlayer use kar rahe hain toh PlayerView use karein, warna VideoView -->
    <!-- app:resize_mode="fit" ya "zoom" se black bars fix honge -->
    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="false"
        app:resize_mode="fit"
        app:surface_type="texture_view" />

    <!-- 2. CUSTOM CONTROLS OVERLAY -->
    <!-- Is layout ko clickable banayein taaki click piche video tak na jaye -->
    <LinearLayout
        android:id="@+id/controlsLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#40000000" 
        android:clickable="true"
        android:focusable="true"
        android:orientation="vertical"
        android:gravity="center_vertical|center_horizontal">

        <!-- CENTER CONTROLS (Play/Pause, Rewind, Forward) -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/btnRewind"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@drawable/ic_replay_10"
                android:background="?attr/selectableItemBackgroundBorderless"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnPlayPause"
                android:layout_width="72dp"
                android:layout_height="72dp"
                android:layout_marginHorizontal="32dp"
                android:src="@drawable/ic_play_arrow"
                android:background="?attr/selectableItemBackgroundBorderless"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnForward"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@drawable/ic_forward_10"
                android:background="?attr/selectableItemBackgroundBorderless"
                app:tint="@android:color/white" />
        </LinearLayout>
    </LinearLayout>

    <!-- 3. BOTTOM CONTROLS (Progress Bar and Time) -->
    <LinearLayout
        android:id="@+id/bottomControls"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:background="#80000000"
        android:orientation="vertical"
        android:padding="16dp"
        app:layout_constraintBottom_toBottomOf="parent">

        <SeekBar
            android:id="@+id/seekBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:progressTint="@android:color/white"
            android:thumbTint="@android:color/white" />

        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp">

            <TextView
                android:id="@+id/tvCurrentTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="00:00"
                android:textColor="@android:color/white"
                android:textSize="14sp" />

            <TextView
                android:id="@+id/tvTotalTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentEnd="true"
                android:text="00:00"
                android:textColor="@android:color/white"
                android:textSize="14sp" />
        </RelativeLayout>
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
