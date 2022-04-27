package com.example.nppump;

import android.app.AlertDialog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.nppump.base.BaseActivity;
import com.example.nppump.databinding.ActivityMainBinding;
import com.example.nppump.vm.MainVM;

public class MainActivity extends BaseActivity<ActivityMainBinding, MainVM> {
    //弹窗Tip
    private AlertDialog TipDialog;
    private AlertDialog.Builder TipDialogBuild;


    @Override
    protected int layoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void initData() {
        click();
    }

    public void showDialogTip(int tipimg, String tiptv) {
        TipDialogBuild = new AlertDialog.Builder(MainActivity.this);
        View view = View.inflate(MainActivity.this, R.layout.dialog_tips, null);
        final ImageView close_img = view.findViewById(R.id.close_img);
        final ImageView tip_img = view.findViewById(R.id.tip_img);
        final TextView tip_tv = view.findViewById(R.id.tip_tv);

        tip_img.setImageResource(tipimg);
        tip_tv.setText(tiptv);

        TipDialogBuild.setView(view);
        TipDialog = TipDialogBuild.show();

        close_img.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View view) {
                TipDialog.dismiss();
            }
        });

    }


//                showDialogTip(R.mipmap.sucess, "参数下发成功！");
//                showDialogTip(R.mipmap.warn,"参数下发失败！");
//                showDialogTip(R.mipmap.warn,"参数下发中，请稍后...");
    /**
     * 点击事件
     */
    private void click() {
        mBinding.xfBtn.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View view) {

            }
        });
    }
}