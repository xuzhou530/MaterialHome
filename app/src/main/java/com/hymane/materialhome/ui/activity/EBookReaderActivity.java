package com.hymane.materialhome.ui.activity;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.hymane.materialhome.R;
import com.hymane.materialhome.api.presenter.impl.EBookReadPresenterImpl;
import com.hymane.materialhome.api.view.IEBookReadView;
import com.hymane.materialhome.bean.http.ebook.BookChapter;
import com.hymane.materialhome.bean.http.ebook.BookChapter.MixToc.Chapters;
import com.hymane.materialhome.bean.http.ebook.ChapterPage;
import com.hymane.materialhome.bean.http.ebook.ChapterRead;
import com.hymane.materialhome.common.Constant;
import com.hymane.materialhome.ui.widget.ReaderViewPager;
import com.hymane.materialhome.utils.BookChapterFactory;
import com.hymane.materialhome.utils.common.UIUtils;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Author   :hymanme
 * Email    :hymanme@163.com
 * Create at 2016/9/29
 * Description: 图书阅读页
 */

public class EBookReaderActivity extends BaseActivity implements IEBookReadView {
    @BindView(R.id.readerViewPager)
    ReaderViewPager mReaderViewPager;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    private String bookId;
    private String bookName;
    private EBookReadPresenterImpl bookReadPresenter;
    private BookChapterFactory chapterFactory;
    private ReaderPagerAdapter readerPagerAdapter;

    private List<Chapters> mBookChapterList;
    private SparseArray<ArrayList<ChapterPage>> pages;
    private int currentChapter = 1;
    private int currentPage = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_reader);
        ButterKnife.bind(this);
        if (savedInstanceState != null) {
            currentChapter = savedInstanceState.getInt("currentChapter");
        }
        super.onCreate(savedInstanceState);

    }

    @Override
    protected void initEvents() {
        bookId = getIntent().getStringExtra("bookId");
        bookName = getIntent().getStringExtra("bookName");
        if (TextUtils.isEmpty(bookId)) {
            setResult(Constant.BOOK_READER_RESULT_FAILED);
            this.finish();
            return;
        }
        setTitle(bookName);
        pages = new SparseArray<>();
        mBookChapterList = new ArrayList<>();
        bookReadPresenter = new EBookReadPresenterImpl(this);
        bookReadPresenter.getBookChapters(bookId);
        readerPagerAdapter = new ReaderPagerAdapter();
        mReaderViewPager.setAdapter(readerPagerAdapter);

        final TextView textView = new TextView(UIUtils.getContext());
        chapterFactory = new BookChapterFactory(bookId, textView.getLineHeight());
    }

    @Override
    public void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showProgress() {

    }

    @Override
    public void hideProgress() {

    }

    @Override
    protected boolean isInitSystemBar() {
        return false;
    }

    @Override
    public void refreshData(Object result) {
        if (result instanceof BookChapter.MixToc) {
            //章节列表
            mBookChapterList.clear();
            mBookChapterList.addAll(((BookChapter.MixToc) result).getChapters());
            for (int i = 0; i < 3; i++) {
                final Chapters chapter = mBookChapterList.get(i);
                bookReadPresenter.getChapterContent(chapter.getLink(), bookId, i + 1, true);
            }
        } else if (result instanceof ChapterRead.Chapter) {
            final int resultId = ((ChapterRead.Chapter) result).getChapterId();
            //阅读内容
            //该书章节列表已经加载完毕
            //阅读当前章节图书内容
            Observable.create((Subscriber<? super ArrayList<ChapterPage>> subscriber) -> {
                //异步操作相关代码
                final ArrayList<ChapterPage> chapterContent = chapterFactory.getChapterContent(resultId);
                if (chapterContent != null) {
                    subscriber.onNext(chapterContent);
                } else {
                    subscriber.onError(new NullPointerException("null chapter content"));
                }
                subscriber.onCompleted();
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<ChapterPage>>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            showMessage(e.toString());
                        }

                        @Override
                        public void onNext(ArrayList<ChapterPage> chapterContent) {
                            pages.append(resultId, chapterContent);
                            readerPagerAdapter.notifyDataSetChanged();
                            mProgressBar.setVisibility(View.GONE);
                        }
                    });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("currentChapter", currentChapter);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        chapterFactory = null;
        super.onDestroy();
    }

    class ReaderPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            if (pages.get(currentChapter) == null) {
                return 0;
            } else {
                return pages.get(currentChapter).size();
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = View.inflate(EBookReaderActivity.this, R.layout.item_reader_page, null);
            TextView tv_book_content = (TextView) view.findViewById(R.id.tv_book_content);
            final TextView title = (TextView) view.findViewById(R.id.tv_chapter_name);
            tv_book_content.setText(pages.get(currentChapter).get(position).getBody());
            final int chapterId = pages.get(currentChapter).get(position).getChapterId();
            title.setText(mBookChapterList.get(chapterId).getTitle());
            container.addView(view);
            return view;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
        }
    }
}
