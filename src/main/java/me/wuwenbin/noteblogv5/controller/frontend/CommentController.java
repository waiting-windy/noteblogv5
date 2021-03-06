package me.wuwenbin.noteblogv5.controller.frontend;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import com.vdurmont.emoji.EmojiParser;
import me.wuwenbin.noteblogv5.constant.DictGroup;
import me.wuwenbin.noteblogv5.constant.NBV5;
import me.wuwenbin.noteblogv5.controller.common.BaseController;
import me.wuwenbin.noteblogv5.model.ResultBean;
import me.wuwenbin.noteblogv5.model.entity.Article;
import me.wuwenbin.noteblogv5.model.entity.Comment;
import me.wuwenbin.noteblogv5.model.entity.Dict;
import me.wuwenbin.noteblogv5.model.entity.Param;
import me.wuwenbin.noteblogv5.service.interfaces.content.ArticleService;
import me.wuwenbin.noteblogv5.service.interfaces.dict.DictService;
import me.wuwenbin.noteblogv5.service.interfaces.mail.MailService;
import me.wuwenbin.noteblogv5.service.interfaces.msg.CommentService;
import me.wuwenbin.noteblogv5.service.interfaces.property.ParamService;
import me.wuwenbin.noteblogv5.util.CacheUtils;
import me.wuwenbin.noteblogv5.util.NbUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

/**
 * created by Wuwenbin on 2018/2/8 at 18:54
 *
 * @author wuwenbin
 */
@Controller
@RequestMapping("/token/comment")
public class CommentController extends BaseController {

    private final ParamService paramService;
    private final ArticleService articleService;
    private final DictService dictService;
    private final CommentService commentService;
    private final MailService mailService;

    public CommentController(ParamService paramService,
                             ArticleService articleService, DictService dictService,
                             CommentService commentService, MailService mailService) {
        this.paramService = paramService;
        this.articleService = articleService;
        this.dictService = dictService;
        this.commentService = commentService;
        this.mailService = mailService;
    }

    @PostMapping("/sub")
    @ResponseBody
    public ResultBean sub(@Valid Comment comment, BindingResult bindingResult, HttpServletRequest request) {
        Param globalCommentOnOff = paramService.findByName("global_comment_onoff");
        Article article = articleService.getById(comment.getArticleId());
        if ("1".equals(globalCommentOnOff.getValue()) && article.getCommented()) {
            if (!bindingResult.hasErrors()) {
                comment.setIpAddr(NbUtils.getRemoteAddress(request));
                boolean develop = NbUtils.getBean(Environment.class).getProperty("app.develop", Boolean.class, true);
                if (!develop) {
                    comment.setIpInfo(NbUtils.getIpInfo(comment.getIpAddr()).getAddress());
                } else {
                    comment.setIpInfo("本地/未知");
                }
                comment.setUserAgent(request.getHeader("user-agent"));
                comment.setComment(
                        HtmlUtil.removeHtmlTag(NbUtils.stripSqlXSS(comment.getComment()),
                                false, "style", "link", "meta", "script"));
                comment.setComment(EmojiParser.parseToHtmlDecimal(comment.getComment()));
                comment.setPost(LocalDateTime.now());
                comment.setClearComment(HtmlUtil.cleanHtmlTag(comment.getComment()));
                List<Dict> keywords = dictService.findList(DictGroup.GROUP_KEYWORD);
                keywords.forEach(
                        kw -> comment.setComment(comment.getComment().replace(kw.getName(), StrUtil.repeat("*", kw.getName().length()))));
                if (StringUtils.isEmpty(comment.getComment())) {
                    return ResultBean.error("评论正确填写评论内容！");
                }
                if (commentService.save(comment)) {
                    if ("1".equals(paramService.findByName(NBV5.COMMENT_MAIL_NOTICE_ONOFF).getValue())) {
                        mailService.sendNoticeMail(basePath(request), articleService.getById(comment.getArticleId()), comment.getComment());
                    }
                    CacheUtils.removeDefaultCache("commentCount");
                    return ResultBean.ok("发表评论成功");
                } else {
                    return ResultBean.error("发表评论失败");
                }
            } else {
                return ajaxJsr303(bindingResult.getFieldErrors());
            }
        } else {
            return ResultBean.error("未开放评论！");
        }
    }
}
