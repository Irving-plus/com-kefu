/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chatopera.cc.app.handler.apps.internet;

import com.alibaba.fastjson.JSON;
import com.chatopera.cc.app.algorithm.AutomaticServiceDist;
import com.chatopera.cc.app.basic.MainContext;
import com.chatopera.cc.app.basic.MainUtils;
import com.chatopera.cc.app.cache.CacheHelper;
import com.chatopera.cc.app.handler.Handler;
import com.chatopera.cc.app.im.message.ChatMessage;
import com.chatopera.cc.app.im.util.RichMediaUtils;
import com.chatopera.cc.app.model.AgentReport;
import com.chatopera.cc.app.model.AgentServiceSatis;
import com.chatopera.cc.app.model.AgentStatus;
import com.chatopera.cc.app.model.AgentUserContacts;
import com.chatopera.cc.app.model.AttachmentFile;
import com.chatopera.cc.app.model.BlackEntity;
import com.chatopera.cc.app.model.Contacts;
import com.chatopera.cc.app.model.CousultInvite;
import com.chatopera.cc.app.model.InviteRecord;
import com.chatopera.cc.app.model.LeaveMsg;
import com.chatopera.cc.app.model.Organ;
import com.chatopera.cc.app.model.SNSAccount;
import com.chatopera.cc.app.model.SessionConfig;
import com.chatopera.cc.app.model.StreamingFile;
import com.chatopera.cc.app.model.SystemConfig;
import com.chatopera.cc.app.model.UKeFuDic;
import com.chatopera.cc.app.model.UkPayAccountEntity;
import com.chatopera.cc.app.model.UkPayMsgEntity;
import com.chatopera.cc.app.model.UploadStatus;
import com.chatopera.cc.app.model.User;
import com.chatopera.cc.app.model.UserHistory;
import com.chatopera.cc.app.persistence.blob.JpaBlobHelper;
import com.chatopera.cc.app.persistence.es.ContactsRepository;
import com.chatopera.cc.app.persistence.impl.UserService;
import com.chatopera.cc.app.persistence.repository.AgentServiceSatisRepository;
import com.chatopera.cc.app.persistence.repository.AgentStatusRepository;
import com.chatopera.cc.app.persistence.repository.AgentUserContactsRepository;
import com.chatopera.cc.app.persistence.repository.AttachmentRepository;
import com.chatopera.cc.app.persistence.repository.ChatMessageRepository;
import com.chatopera.cc.app.persistence.repository.ConsultInviteRepository;
import com.chatopera.cc.app.persistence.repository.InviteRecordRepository;
import com.chatopera.cc.app.persistence.repository.LeaveMsgRepository;
import com.chatopera.cc.app.persistence.repository.OrganRepository;
import com.chatopera.cc.app.persistence.repository.SNSAccountRepository;
import com.chatopera.cc.app.persistence.repository.StreamingFileRepository;
import com.chatopera.cc.app.persistence.repository.UkPayAccountRepository;
import com.chatopera.cc.app.persistence.repository.UkPayMsgRepository;
import com.chatopera.cc.app.persistence.repository.UserRepository;
import com.chatopera.cc.constant.CommonConstant;
import com.chatopera.cc.util.BrowserClient;
import com.chatopera.cc.util.CheckMobile;
import com.chatopera.cc.util.CommonUtil;
import com.chatopera.cc.util.Constants;
import com.chatopera.cc.util.ExtUtils;
import com.chatopera.cc.util.IP;
import com.chatopera.cc.util.IPTools;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.OnlineUserUtils;
import com.chatopera.cc.util.StreamingFileUtils;
import com.chatopera.cc.util.WebIMClient;
import com.hazelcast.aws.utility.StringUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/im")
@EnableAsync
public class IMController extends Handler {
	private final static Logger logger = LoggerFactory.getLogger(IMController.class);

	@Value("${uk.im.server.host}")
	private String host;
	@Value("${uk.im.server.port}")
	private Integer port;
	@Value("${cs.im.server.ssl.port}")
	private Integer sslPort;
	@Value("${web.upload-path}")
	private String path;
	@Value("${ext.upload-max-num:50}")
	private int uploadMaxNum;
	@Value("${ext.upload-max-size:3145728}")
	private long uploadMaxSize;
	@Autowired
	private StreamingFileRepository streamingFileRepository;
	@Autowired
	private JpaBlobHelper jpaBlobHelper;
	@Autowired
	private ConsultInviteRepository inviteRepository;
	@Autowired
	private ChatMessageRepository chatMessageRes;
	@Autowired
	private AgentServiceSatisRepository agentServiceSatisRes;
	@Autowired
	private InviteRecordRepository inviteRecordRes;
	@Autowired
	private LeaveMsgRepository leaveMsgRes;
	@Autowired
	private AttachmentRepository attachementRes;
	@Autowired
	private ContactsRepository contactsRes;
	@Autowired
	private AgentUserContactsRepository agentUserContactsRes;
	@Autowired
	private SNSAccountRepository snsAccountRepository;
	@Autowired
	private UserRepository userRepository;
	private HashOperations<String, String, String> redisHashOps;
	//////////----->自动回复客服名称    by Wayne on 2019/9/3 19:30   start--->
//	@Value("${pay.auto.answer.customer.service.name}")
//	private String payAutoAnswerCustomerServiceName;
	@Autowired
	private UkPayMsgRepository ukPayMsgRepository;
	@Autowired
	private UkPayAccountRepository ukPayAccountRepository;
	//////////<-----自动回复客服名称    by Wayne on 2019/9/3 19:30    <---end
	//////////----->获取在线客服列表相关    by Wayne on 2019/9/10 17:48   start--->
	@Autowired
	private AgentStatusRepository agentStatusRepository;
	@Autowired
	private OrganRepository organRes;
	//////////<-----获取在线客服列表相关    by Wayne on 2019/9/10 17:48    <---end
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * 使用StringRedisTemplate而不是RedisTemplate解决序列化问题
	 * https://stackoverflow.com/questions/13215024/weird-redis-key-with-spring-data-jedis
	 */
	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private UserService userService;

	@PostConstruct
	private void init() {
		redisHashOps = redis.opsForHash();
	}

	@RequestMapping("/{id}")
	@Menu(type = "im", subtype = "point", access = true)
	public ModelAndView point(HttpServletRequest request, HttpServletResponse response, @PathVariable String id,
	                          @Valid String orgi, @Valid String userid, @Valid String title, @Valid String aiid) {
		ModelAndView view = request(super.createRequestPageTempletResponse("/apps/im/point"));
		String sessionid = request.getSession().getId();
		if (StringUtils.isNotBlank(id)) {
			view.addObject("hostname", request.getServerName());
			SystemConfig systemConfig = MainUtils.getSystemConfig();
			if (systemConfig != null && systemConfig.isEnablessl()) {
				view.addObject("schema", "https");
				if (request.getServerPort() == 80) {
					view.addObject("port", 443);
				} else {
					view.addObject("port", request.getServerPort());
				}
			} else {
				view.addObject("schema", request.getScheme());
				view.addObject("port", request.getServerPort());
			}
			view.addObject("appid", id);
			view.addObject("client", MainUtils.getUUID());
			view.addObject("sessionid", sessionid);
			view.addObject("ip", MainUtils.md5(request.getRemoteAddr()));
			view.addObject("mobile", CheckMobile.check(request.getHeader("User-Agent")));
			CousultInvite invite = OnlineUserUtils.cousult(id, orgi, inviteRepository);
			if (invite != null) {
				orgi = invite.getOrgi();
				view.addObject("inviteData", invite);
				view.addObject("orgi", invite.getOrgi());
				view.addObject("appid", id);

				if (StringUtils.isNotBlank(aiid)) {
					view.addObject("aiid", aiid);
				} else if (StringUtils.isNotBlank(invite.getAiid())) {
					view.addObject("aiid", invite.getAiid());
				}
				// 记录用户行为日志
				UserHistory userHistory = new UserHistory();
				String url = request.getHeader("referer");
				if (StringUtils.isNotBlank(url)) {
					if (url.length() > 255) {
						userHistory.setUrl(url.substring(0, 255));
					} else {
						userHistory.setUrl(url);
					}
					userHistory.setReferer(userHistory.getUrl());
				}
				userHistory.setParam(MainUtils.getParameter(request));
				if (userHistory != null) {
					userHistory.setMaintype("im");
					userHistory.setSubtype("point");
					userHistory.setName("online");
					userHistory.setAdmin(false);
					userHistory.setAccessnum(true);
				}
				User imUser = super.getIMUser(request, userid, null);
				if (imUser != null) {
					userHistory.setCreater(imUser.getId());
					userHistory.setUsername(imUser.getUsername());
					userHistory.setOrgi(orgi);
				}
				if (StringUtils.isNotBlank(title)) {
					if (title.length() > 255) {
						userHistory.setTitle(title.substring(0, 255));
					} else {
						userHistory.setTitle(title);
					}
				}
				userHistory.setOrgi(invite.getOrgi());
				userHistory.setAppid(id);
				userHistory.setSessionid(sessionid);
				String ip = MainUtils.getIpAddr(request);
				userHistory.setHostname(ip);
				userHistory.setIp(ip);
				IP ipdata = IPTools.getInstance().findGeography(ip);
				userHistory.setCountry(ipdata.getCountry());
				userHistory.setProvince(ipdata.getProvince());
				userHistory.setCity(ipdata.getCity());
				userHistory.setIsp(ipdata.getIsp());
				BrowserClient client = MainUtils.parseClient(request);
				userHistory.setOstype(client.getOs());
				userHistory.setBrowser(client.getBrowser());
				userHistory.setMobile(CheckMobile.check(request.getHeader("User-Agent")) ? "1" : "0");
				if (invite.isSkill()) {
					/***
					 * 查询 技能组 ， 缓存？
					 */
					view.addObject("skillList", OnlineUserUtils.organ(orgi, ipdata, invite, true));
					/**
					 * 查询坐席 ， 缓存？
					 */
					view.addObject("agentList", OnlineUserUtils.agents(orgi, true));
				}
				view.addObject("traceid", userHistory.getId());
				if (invite.isRecordhis()) {
					MainUtils.published(userHistory);
				}

				view.addObject("pointAd", MainUtils.getPointAdv(MainContext.AdPosEnum.POINT.toString(), orgi));
				view.addObject("inviteAd", MainUtils.getPointAdv(MainContext.AdPosEnum.INVITE.toString(), orgi));
			}
		}

		return view;
	}

	@RequestMapping("/{id}/userlist")
	@Menu(type = "im", subtype = "inlist", access = true)
	public void inlist(HttpServletRequest request, HttpServletResponse response, @PathVariable String id,
	                   @Valid String userid) throws IOException {
		response.setHeader("Content-Type", "text/html;charset=utf-8");
		if (StringUtils.isNotBlank(userid)) {
			BlackEntity black = (BlackEntity) CacheHelper.getSystemCacheBean().getCacheObject(userid,
					MainContext.SYSTEM_ORGI);
			if ((black != null && (black.getEndtime() == null || black.getEndtime().after(new Date())))) {
				response.getWriter().write("in");
			}
		}
	}

	/**
	 * 获取在线客服接口
	 * @param response
	 */
	@RequestMapping("/getOnlineCustomerServiceList")
	public void getOnlineCustomerServiceList(HttpServletResponse response) {
		//////////----->忽略orgi    by Wayne on 2019/9/10 16:22   start--->
		List<AgentStatus> agentStatusList = agentStatusRepository.findAll();
		for (int i = 0; i < agentStatusList.size(); ) {
			AgentStatus agentStatus = agentStatusList.get(i);
			if (CacheHelper.getAgentStatusCacheBean().getCacheObject(agentStatus.getAgentno()) == null) {
				agentStatusRepository.delete(agentStatus);
				agentStatusList.remove(i);
				continue;
			} else {
				AgentStatus temp = (AgentStatus) CacheHelper.getAgentStatusCacheBean().getCacheObject(agentStatus.getAgentno());
				//////////<-----忽略orgi    by Wayne on 2019/9/10 16:22    <---end
				agentStatusList.set(i, temp);
			}
			i++;
		}
		List<String> skillList = new ArrayList<String>();
		for (AgentStatus agentStatus : agentStatusList) {
			if (!StringUtils.isBlank(agentStatus.getSkill())) {
				skillList.add(agentStatus.getSkill());
			}
		}
		if (skillList.size() > 0) {
			List<Organ> organList = organRes.findAll(skillList);
			for (AgentStatus agentStatus : agentStatusList) {
				if (!StringUtils.isBlank(agentStatus.getSkill())) {
					for (Organ organ : organList) {
						if (agentStatus.getSkill().equals(organ.getId())) {
							agentStatus.setSkillname(organ.getName());
							break;
						}
					}
				}
			}
		}
		writeJson(response, JSON.toJSONString(agentStatusList));
	}

	private void writeJson(HttpServletResponse response, String jsonString) {
		PrintWriter out = null;
		try {
			//设定类容为json的格式
			response.setContentType("application/json;charset=UTF-8");
			out = response.getWriter();
			//写到客户端
			out.write(jsonString);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * 延时获取用户端浏览器的跟踪ID
	 *
	 * @param request
	 * @param response
	 * @param orgi
	 * @param appid
	 * @param userid
	 * @param sign
	 * @return
	 */
	@RequestMapping("/online")
	@Menu(type = "im", subtype = "online", access = true)
	public SseEmitter callable(HttpServletRequest request, HttpServletResponse response, @Valid Contacts contacts,
	                           final @Valid String orgi, final @Valid String sessionid, @Valid String appid, final @Valid String userid,
	                           @Valid String sign, final @Valid String client, final @Valid String title, final @Valid String traceid) {
		BlackEntity black = (BlackEntity) CacheHelper.getSystemCacheBean().getCacheObject(userid, orgi);
		SseEmitter retSseEmitter = null;
		if ((black == null || (black.getEndtime() != null && black.getEndtime().before(new Date())))) {
			final SseEmitter emitter = new SseEmitter(30000L);
			if (CacheHelper.getSystemCacheBean().getCacheObject(userid, orgi) == null) {
				if (StringUtils.isNotBlank(userid)) {
					emitter.onCompletion(new Runnable() {
						@Override
						public void run() {
							try {
								OnlineUserUtils.webIMClients.removeClient(userid, client, false); // 执行了 邀请/再次邀请后终端的
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					emitter.onTimeout(new Runnable() {
						@Override
						public void run() {
							try {
								if (emitter != null) {
									emitter.complete();
								}
								OnlineUserUtils.webIMClients.removeClient(userid, client, true); // 正常的超时断开
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					CousultInvite invite = OnlineUserUtils.cousult(appid, orgi, inviteRepository);
					if (invite != null && invite.isTraceuser()) {
						contacts = processContacts(orgi, contacts, appid, userid);
					}
					if (StringUtils.isNotBlank(sign)) {
						OnlineUserUtils.online(
								super.getIMUser(request, sign, contacts != null ? contacts.getName() : null), orgi,
								sessionid, MainContext.OnlineUserTypeStatus.WEBIM.toString(), request,
								MainContext.ChannelTypeEnum.WEBIM.toString(), appid, contacts, invite);

					}
					OnlineUserUtils.webIMClients.putClient(userid, new WebIMClient(userid, client, emitter));
				}
			}
			retSseEmitter = emitter;
		}
		return retSseEmitter;
	}

	/**
	 * 聊天页面渲染接口
	 *      根据text接口处理先关信息后跳转过来,
	 *          进行玩家名称处理
	 *          ->注入支付相关信息
	 *          ->页面模板选择
	 */
	@RequestMapping("/index")
	@Menu(type = "im", subtype = "index", access = true)
	public ModelAndView index(ModelMap map, HttpServletRequest request, HttpServletResponse response,
	                          @Valid String orgi, @Valid String aiid, @Valid String traceid, @Valid String exchange, @Valid String title,
	                          @Valid String url, @Valid String mobile, @Valid String phone, @Valid String ai, @Valid String client,
	                          @Valid String type, @Valid String appid, @Valid String userid, @Valid String sessionid, @Valid String skill,
	                          @Valid String agent, @Valid Contacts contacts, @Valid String product, @Valid String description,
	                          @Valid String imgurl, @Valid String pid, @Valid String purl, @Valid String userPin, @Valid String chatType) throws Exception {

		String ip = ExtUtils.getIpAddress(request);
		String nickname = null;
		ModelAndView view = request(super.createRequestPageTempletResponse("/apps/im/index"));
		//////////----->传递askType,确认是否为支付相关咨询    by Wayne on 2019/9/10 13:38   start--->
		request.setAttribute("chatType", chatType);
		//////////<-----传递askType,确认是否为支付相关咨询    by Wayne on 2019/9/10 13:38    <---end
		BlackEntity black = (BlackEntity) CacheHelper.getSystemCacheBean().getCacheObject(userid,
				MainContext.SYSTEM_ORGI);
		if (StringUtils.isNotBlank(appid)
				&& (black == null || (black.getEndtime() != null && black.getEndtime().before(new Date())))) {
			CousultInvite invite = OnlineUserUtils.cousult(appid, orgi, inviteRepository);
			// 获取玩家ID sin 2019-08-17 18:39:43 start
			if (StringUtils.isNotBlank(userPin)) {
				nickname = "玩家ID:" + userPin;
				// 若前段传递pin过来,则刷新之前的游客用户名
				userService.refreshNickName(userid, nickname);
			} else {
				nickname = "游客:" + ip;
			}

			// 获取玩家ID和登录类型 sin 2019-08-17 18:39:43 end
			boolean consult = true; // 是否已收集用户信息
			SessionConfig sessionConfig = AutomaticServiceDist.initSessionConfig(orgi);

			map.addAttribute("sessionConfig", sessionConfig);
			map.addAttribute("hostname", request.getServerName());

			if (sslPort != null) {
				map.addAttribute("port", sslPort);
			} else {
				map.addAttribute("port", port);
			}

			map.addAttribute("appid", appid);
			map.addAttribute("userid", userid);
			map.addAttribute("schema", request.getScheme());
			map.addAttribute("sessionid", sessionid);

			view.addObject("product", product);
			view.addObject("description", description);
			view.addObject("imgurl", imgurl);
			view.addObject("pid", pid);
			view.addObject("purl", purl);

			map.addAttribute("ip", MainUtils.md5(request.getRemoteAddr()));

			if (StringUtils.isNotBlank(traceid)) {
				map.addAttribute("traceid", traceid);
			}
			if (StringUtils.isNotBlank(exchange)) {
				map.addAttribute("exchange", exchange);
			}
			if (StringUtils.isNotBlank(title)) {
				map.addAttribute("title", title);
			}
			if (StringUtils.isNotBlank(traceid)) {
				map.addAttribute("url", url);
			}

			map.addAttribute("ukefport", request.getServerPort());
			/**
			 * 先检查 invite不为空
			 */
			if (invite != null) {

				//////////----->传递orgi    by Wayne on 2019/9/10 13:10   start--->
				invite.setOrgi(orgi);
				//////////<-----传递orgi    by Wayne on 2019/9/10 13:10    <---end

				map.addAttribute("orgi", invite.getOrgi());
				map.addAttribute("inviteData", invite);
				map.addAttribute("aiid", invite.getAiid());

				AgentReport report = AutomaticServiceDist.getAgentReport(invite.getOrgi());

				if (report.getAgents() == 0 || (sessionConfig.isHourcheck()
						&& !MainUtils.isInWorkingHours(sessionConfig.getWorkinghours()) && invite.isLeavemessage())) {
					view = request(super.createRequestPageTempletResponse("/apps/im/leavemsg"));
				} else if (invite.isConsult_info()) { // 启用了信息收集 , 从Request获取 ， 或从 Cookies 里去
					// 验证 OnlineUser 信息
					if (contacts != null && StringUtils.isNotBlank(contacts.getName())) { // contacts用于传递信息，并不和 联系人表发生
						// 关联，contacts信息传递给
						// Socket.IO，然后赋值给
						// AgentUser，最终赋值给
						// AgentService永久存储
						consult = true;
						// 存入 Cookies
						if (invite.isConsult_info_cookies()) {
							Cookie name = new Cookie("name",
									MainUtils.encryption(URLEncoder.encode(contacts.getName(), "UTF-8")));
							response.addCookie(name);
							name.setMaxAge(3600);
							if (StringUtils.isNotBlank(contacts.getPhone())) {
								Cookie phonecookie = new Cookie("phone",
										MainUtils.encryption(URLEncoder.encode(contacts.getPhone(), "UTF-8")));
								phonecookie.setMaxAge(3600);
								response.addCookie(phonecookie);
							}
							if (StringUtils.isNotBlank(contacts.getEmail())) {
								Cookie email = new Cookie("email",
										MainUtils.encryption(URLEncoder.encode(contacts.getEmail(), "UTF-8")));
								email.setMaxAge(3600);
								response.addCookie(email);
							}
							if (StringUtils.isNotBlank(contacts.getMemo())) {
								Cookie memo = new Cookie("memo",
										MainUtils.encryption(URLEncoder.encode(contacts.getName(), "UTF-8")));
								memo.setMaxAge(3600);
								response.addCookie(memo);
							}
						}
					} else {
						// 从 Cookies里尝试读取
						if (invite.isConsult_info_cookies()) {
							Cookie[] cookies = request.getCookies();// 这样便可以获取一个cookie数组
							contacts = new Contacts();
							if (cookies != null) {
								for (Cookie cookie : cookies) {
									if (cookie != null && StringUtils.isNotBlank(cookie.getName())
											&& StringUtils.isNotBlank(cookie.getValue())) {
										if (cookie.getName().equals("name")) {
											contacts.setName(URLDecoder.decode(MainUtils.decryption(cookie.getValue()),
													"UTF-8"));
										}
										if (cookie.getName().equals("phone")) {
											contacts.setPhone(URLDecoder.decode(MainUtils.decryption(cookie.getValue()),
													"UTF-8"));
										}
										if (cookie.getName().equals("email")) {
											contacts.setEmail(URLDecoder.decode(MainUtils.decryption(cookie.getValue()),
													"UTF-8"));
										}
										if (cookie.getName().equals("memo")) {
											contacts.setMemo(URLDecoder.decode(MainUtils.decryption(cookie.getValue()),
													"UTF-8"));
										}
									}
								}
							}
						}
						if (StringUtils.isBlank(contacts.getName())) {
							consult = false;
							view = request(super.createRequestPageTempletResponse("/apps/im/collecting"));
						}
					}
				} else {
					contacts = processContacts(invite.getOrgi(), contacts, appid, userid);
				}

				if (StringUtils.isNotBlank(client)) {
					map.addAttribute("client", client);
				}
				if (StringUtils.isNotBlank(skill)) {
					map.addAttribute("skill", skill);
				}
				if (StringUtils.isNotBlank(agent)) {
					map.addAttribute("agent", agent);
				}

				map.addAttribute("contacts", contacts);

				if (StringUtils.isNotBlank(type)) {
					map.addAttribute("type", type);
				}
				IP ipdata = IPTools.getInstance().findGeography(MainUtils.getIpAddr(request));
				map.addAttribute("skillList", OnlineUserUtils.organ(invite.getOrgi(), ipdata, invite, true));

				if (invite != null && consult) {
					if (contacts != null && StringUtils.isNotBlank(contacts.getName())) {
						nickname = contacts.getName();
					}
					map.addAttribute("username", nickname);
					if (MainContext.model.get("chatbot") != null && StringUtils.isNotBlank(invite.getAiid())
							&& invite.isAi()
							&& ((StringUtils.equals(ai, "true")) || (invite.isAifirst() && ai == null))) { // 启用 AI ， 并且
						// AI优先 接待
						HashMap<String, String> chatbotConfig = new HashMap<String, String>();
						chatbotConfig.put("botname", invite.getAiname());
						chatbotConfig.put("botid", invite.getAiid());
						chatbotConfig.put("botwelcome", invite.getAimsg());
						chatbotConfig.put("botfirst", Boolean.toString(invite.isAifirst()));
						chatbotConfig.put("isai", Boolean.toString(invite.isAi()));
						if (chatbotConfig != null) {
							map.addAttribute("chatbotConfig", chatbotConfig);
						}
						view = request(super.createRequestPageTempletResponse("/apps/im/chatbot/index"));
						if (CheckMobile.check(request.getHeader("User-Agent")) || StringUtils.isNotBlank(mobile)) {
							view = request(super.createRequestPageTempletResponse("/apps/im/chatbot/mobile")); // 智能机器人
							// 移动端
						}
					} else {
						if (CheckMobile.check(request.getHeader("User-Agent")) || StringUtils.isNotBlank(mobile)) {
							view = request(super.createRequestPageTempletResponse("/apps/im/mobile")); // WebIM移动端。再次点选技能组？
						}
					}


					//////////----->玩家端不显示历史聊天记录    by Wayne on 2019/9/23 13:50   start--->
					//////////----->不考虑orgi,方便用户看到与不同客服的聊天历史    by Wayne on 2019/9/18 14:41   start--->
//					Page<ChatMessage> chatMessageList = chatMessageRes.findByUsessionAndOrgi(userid, orgi, new PageRequest(0, 20, Direction.DESC, "updatetime"));
//					Page<ChatMessage> chatMessageList = chatMessageRes.findByUsession(userid, new PageRequest(0, 20, Direction.DESC, "updatetime"));
					//////////<-----不考虑orgi,方便用户看到与不同客服的聊天历史    by Wayne on 2019/9/18 14:41    <---end
//					// 替换新的用户名
//					replaceNickName(nickname, chatMessageList);
//					map.addAttribute("chatMessageList", chatMessageList);
					//////////<-----玩家端不显示历史聊天记录    by Wayne on 2019/9/23 13:50    <---end


				}
				view.addObject("commentList", UKeFuDic.getInstance().getDic(MainContext.UKEFU_SYSTEM_COMMENT_DIC));
				view.addObject("commentItemList",
						UKeFuDic.getInstance().getDic(MainContext.UKEFU_SYSTEM_COMMENT_ITEM_DIC));
				view.addObject("welcomeAd", MainUtils.getPointAdv(MainContext.AdPosEnum.WELCOME.toString(), orgi));
				view.addObject("imageAd", MainUtils.getPointAdv(MainContext.AdPosEnum.IMAGE.toString(), orgi));
				// OnlineUserUtils.sendWebIMClients(userid , "accept");

				if (invite.isTraceuser()) {
					Page<InviteRecord> inviteRecordList = inviteRecordRes.findByUseridAndOrgi(userid, orgi,
							new PageRequest(0, 1, Direction.DESC, "createtime"));
					if (inviteRecordList.getContent() != null && inviteRecordList.getContent().size() > 0) {
						InviteRecord record = inviteRecordList.getContent().get(0);
						record.setUpdatetime(new Date());
						record.setTraceid(traceid);
						record.setTitle(title);
						record.setUrl(url);
						record.setResponsetime((int) (System.currentTimeMillis() - record.getCreatetime().getTime()));
						record.setResult(MainContext.OnlineUserInviteStatus.ACCEPT.toString());
						inviteRecordRes.save(record);
					}
				}
			}
		}

		//////////----->放入支付相关信息    by Wayne on 2019/9/4 13:14   start--->
		if (StringUtils.equals(chatType, "1")) {
			putPayInfo(view, orgi);
		}
		//////////<-----支付相关    by Wayne on 2019/9/4 13:14    <---end
		view.addObject("userRandom", CommonUtil.string2NumberByMod(orgi, 5));
		view.addObject("playerRandom", CommonUtil.string2NumberByMod(nickname, 6));
		return view;
	}

	/**
	 * 向聊天页面注入支付相关信息
	 */
	private void putPayInfo(ModelAndView view, String kefuPin) {
		String kefuName = "充值小助手";
		final User kefu = userRepository.findByUsernameAndDatastatus(kefuPin, false);
		if (kefu != null) {
			kefuName = kefu.getUname();
		}
		view.addObject("payAutoAnswerCustomerServiceName", kefuName);

		//////////----->只显示对应客服录入的支付方式    by Wayne on 2019/9/13 14:07   start--->
//		List<UkPayMsgEntity> ukPayMsgEntityList = ukPayMsgRepository.findByStatusNot(0);
		final List<UkPayMsgEntity> ukPayMsgEntityList = ukPayMsgRepository.findByCreateUserAndStatus(kefuPin, CommonConstant.NORMAL_STATUS);
		//////////<-----只显示对应客服录入的支付方式    by Wayne on 2019/9/13 14:07    <---end

		if (ukPayMsgEntityList == null || ukPayMsgEntityList.size() == 0) {
			throw new RuntimeException("您选择的客服暂无对应的充值方式,请选择其他客服!");
		}
		List<Map> payInfoList = new ArrayList<>(ukPayMsgEntityList.size());
		for (int i = 0; i < ukPayMsgEntityList.size(); i++) {
			Integer id = ukPayMsgEntityList.get(i).getId();
			String msg = ukPayMsgEntityList.get(i).getMsg();
			String name = ukPayMsgEntityList.get(i).getName();
			String code = ukPayMsgEntityList.get(i).getCode();
			Integer type = ukPayMsgEntityList.get(i).getType();
			String payInfoMsg = null;
			// 随机获取相关账号,加上权重功能
			List<UkPayAccountEntity> ukPayAccountEntityList = ukPayAccountRepository.findByPayFuctionIdAndTypeAndStatus(id, type, CommonConstant.NORMAL_STATUS);
			if (ukPayAccountEntityList == null || ukPayAccountEntityList.size() == 0) {
				if (type == 1) {
					throw new RuntimeException("您选择的客服暂无" + name + "账号,请选择其他客服!");
				} else {
					throw new RuntimeException("您选择的客服暂无" + name + "二维码,请选择其他客服!");
				}
			}

			final UkPayAccountEntity ukPayAccountEntity = getPayAccountByWeightsRandomly(ukPayAccountEntityList);
			final String account = ukPayAccountEntity.getAccount();
			final String accountUsername = ukPayAccountEntity.getAccountUsername();
			if (type == 1) {
				payInfoMsg = MessageFormat.format(msg, account, accountUsername);
			} else {
				payInfoMsg = msg;
			}
			Map payInfoMap = new HashMap<>();
			payInfoMap.put("payInfoName", name);
			payInfoMap.put("payInfoMsg", payInfoMsg);
			payInfoMap.put("payInfoType", type);
			payInfoMap.put("payInfoQRCodeUrl", account);
			payInfoMap.put("payFunctionId", id);
			payInfoMap.put("payFunctionCode", code);

			//////////----->放入支付信息图片    by Wayne on 2019/9/18 15:00   start--->
			String payImgUrl = null;
			if (StringUtils.contains(name, CommonConstant.ALIPAY_NAME)) {
				payImgUrl = CommonConstant.ALIPAY_IMG_URL;
			} else if (StringUtils.contains(name, CommonConstant.WECHATPAY_NAME)) {
				payImgUrl = CommonConstant.WECHATPAY_IMG_URL;
			} else if (StringUtils.contains(name, CommonConstant.BANKCANDPAY_NAME)) {
				payImgUrl = CommonConstant.BANKCANDPAY_IMG_URL;
			}
			// <a href='payInfoView.html?kefuPin={0}&payFunctionId={1}'><img src='{2}'></a>
			final String payImgLabel = String.format(CommonConstant.PAY_IMG_LABLE,payImgUrl, kefuPin, id);
			payInfoMap.put("payImgLabel", payImgLabel);
			//////////<-----放入支付信息图片    by Wayne on 2019/9/18 15:00    <---end
			payInfoList.add(payInfoMap);
		}
		view.addObject("payInfoList", payInfoList);
		view.addObject("RECHARGE_AUTO_REPLY", CommonConstant.RECHARGE_AUTO_REPLY);
		view.addObject("kefuPin", kefuPin);

//		System.out.println(CommonUtil.getFileCount(CommonConstant.KEFU_AVATAR_PATH));
	}

	/**
	 * 随机获取相关账号,加上权重功能
	 *
	 * @param ukPayAccountEntityList
	 * @return
	 */
	private UkPayAccountEntity getPayAccountByWeightsRandomly(List<UkPayAccountEntity> ukPayAccountEntityList) {
		List<UkPayAccountEntity> accountList = new ArrayList<>();
		for (UkPayAccountEntity ukPayAccountEntity : ukPayAccountEntityList) {
			for (int t = 0; t <= ukPayAccountEntity.getWeights(); t++) {
				accountList.add(ukPayAccountEntity);
			}
		}
		return accountList.get(RandomUtils.nextInt(accountList.size()));
	}

	/**
	 * 更新玩家在聊天窗口显示的用户名
	 *
	 * @param nickname
	 * @param chatMessageList
	 */
	private void replaceNickName(String nickname, Page<ChatMessage> chatMessageList) {
		for (ChatMessage chatMessage : chatMessageList.getContent()) {
			if (StringUtils.equals(chatMessage.getCalltype(), "呼入")) {
				chatMessage.setUsername(nickname);
			}
		}
	}

	private Contacts processContacts(String orgi, Contacts contacts, String appid, String userid) {
		if (contacts != null) {
			if (contacts != null && (StringUtils.isNotBlank(contacts.getName())
					|| StringUtils.isNotBlank(contacts.getPhone()) || StringUtils.isNotBlank(contacts.getEmail()))) {
				StringBuffer query = new StringBuffer();
				query.append(contacts.getName());
				if (StringUtils.isNotBlank(contacts.getPhone())) {
					query.append(" OR ").append(contacts.getPhone());
				}
				if (StringUtils.isNotBlank(contacts.getEmail())) {
					query.append(" OR ").append(contacts.getEmail());
				}
				Page<Contacts> contactsList = contactsRes.findByOrgi(orgi, false, query.toString(),
						new PageRequest(0, 1));
				if (contactsList.getContent().size() > 0) {
					contacts = contactsList.getContent().get(0);
				} else {
					// contactsRes.save(contacts) ; //需要增加签名验证，避免随便产生垃圾信息，也可以自行修改？
					contacts.setId(null);
				}
			} else {
				contacts.setId(null);
			}
			if (contacts != null && StringUtils.isNotBlank(contacts.getId())) {
				List<AgentUserContacts> agentUserContactsList = agentUserContactsRes.findByUseridAndOrgi(userid, orgi);
				if (agentUserContactsList.size() == 0) {
					AgentUserContacts agentUserContacts = new AgentUserContacts();
					agentUserContacts.setAppid(appid);
					agentUserContacts.setChannel(MainContext.ChannelTypeEnum.WEBIM.toString());
					agentUserContacts.setContactsid(contacts.getId());
					agentUserContacts.setUserid(userid);
					agentUserContacts.setOrgi(orgi);
					agentUserContacts.setCreatetime(new Date());
					agentUserContactsRes.save(agentUserContacts);
				}
			} else if (StringUtils.isNotBlank(userid)) {
				List<AgentUserContacts> agentUserContactsList = agentUserContactsRes.findByUseridAndOrgi(userid, orgi);
				if (agentUserContactsList.size() > 0) {
					AgentUserContacts agentUserContacts = agentUserContactsList.get(0);
					contacts = contactsRes.findOne(agentUserContacts.getContactsid());
				}
			}
		}
		return contacts;
	}

	@RequestMapping("/text/{appid}")
	@Menu(type = "im", subtype = "index", access = true)
	public ModelAndView text(HttpServletRequest request, HttpServletResponse response, @PathVariable String appid,
	                         @Valid String traceid, @Valid String aiid, @Valid String exchange, @Valid String title, @Valid String url,
	                         @Valid String skill, @Valid String id, @Valid String userid, @Valid String agent, @Valid String name,
	                         @Valid String email, @Valid String phone, @Valid String ai, @Valid String kefuPin, @Valid String product,
	                         @Valid String description, @Valid String imgurl, @Valid String pid, @Valid String purl, @Valid String userPin, @Valid String chatType)
			throws Exception {
		ModelAndView view = request(super.createRequestPageTempletResponse("/apps/im/text"));

		//////////----->传递chatType,确认是否为支付相关咨询    by Wayne on 2019/9/10 13:38   start--->
		// chatType(1->充值咨询,2->普通咨询,3->彩票咨询)
		view.addObject("chatType", chatType);
		//////////<-----传递chatType,确认是否为支付相关咨询    by Wayne on 2019/9/10 13:38    <---end

		view.addObject("hostname", request.getServerName());
		view.addObject("port", request.getServerPort());
		view.addObject("schema", request.getScheme());
		view.addObject("appid", appid);
		view.addObject("userPin", userPin);

		view.addObject("ip", MainUtils.md5(request.getRemoteAddr()));

		if (StringUtils.isNotBlank(skill)) {
			view.addObject("skill", skill);
		}
		if (StringUtils.isNotBlank(agent)) {
			view.addObject("agent", agent);
		}

		view.addObject("client", MainUtils.getUUID());
		view.addObject("sessionid", request.getSession().getId());

		view.addObject("id", id);
		if (StringUtils.isNotBlank(ai)) {
			view.addObject("ai", ai);
		}
		if (StringUtils.isNotBlank(exchange)) {
			view.addObject("exchange", exchange);
		}

		view.addObject("name", name);
		view.addObject("email", email);
		view.addObject("phone", phone);
		view.addObject("userid", userid);

		view.addObject("product", product);
		view.addObject("description", description);
		view.addObject("imgurl", imgurl);
		view.addObject("pid", pid);
		view.addObject("purl", purl);

		if (StringUtils.isNotBlank(traceid)) {
			view.addObject("traceid", traceid);
		}
		if (StringUtils.isNotBlank(title)) {
			view.addObject("title", title);
		}
		if (StringUtils.isNotBlank(traceid)) {
			view.addObject("url", url);
		}
		CousultInvite invite = OnlineUserUtils.cousult(appid, kefuPin, inviteRepository);
		if (invite != null) {
			view.addObject("inviteData", invite);
//			view.addObject("kefuPin", invite.getOrgi());

			//////////----->自定义客服跳转    by Wayne on 2019/9/10 10:28   start--->
//			if (StringUtils.isBlank(kefuPin)) {
//				view.addObject("orgi", invite.getOrgi());
//			} else {
				view.addObject("orgi", kefuPin);
//			}
			//////////<-----自定义客服跳转    by Wayne on 2019/9/10 10:28    <---end

			view.addObject("appid", appid);

			if (StringUtils.isNotBlank(aiid)) {
				view.addObject("aiid", aiid);
			} else if (StringUtils.isNotBlank(invite.getAiid())) {
				view.addObject("aiid", invite.getAiid());
			}
		}

		return view;
	}

	@RequestMapping("/leavemsg/save")
	@Menu(type = "admin", subtype = "user")
	public ModelAndView leavemsgsave(HttpServletRequest request, @Valid String appid, @Valid LeaveMsg msg) {
		if (StringUtils.isNotBlank(appid)) {
			SNSAccount snsAccount = snsAccountRepository.findBySnsid(appid);
			String orgi = snsAccount.getOrgi();
			CousultInvite invite = inviteRepository.findBySnsaccountidAndOrgi(appid, orgi);
			// List<LeaveMsg> msgList = leaveMsgRes.findByOrgiAndUserid(invite.getOrgi(),
			// msg.getUserid());
			// if(msg!=null && msgList.size() == 0){
			if (msg != null) {
				msg.setOrgi(invite.getOrgi());
				leaveMsgRes.save(msg);
			}
		}
		return request(super.createRequestPageTempletResponse("/apps/im/leavemsgsave"));
	}

	@RequestMapping("/refuse")
	@Menu(type = "im", subtype = "refuse", access = true)
	public void refuse(HttpServletRequest request, HttpServletResponse response, @Valid String orgi,
	                   @Valid String appid, @Valid String userid, @Valid String sessionid, @Valid String client) throws Exception {
		OnlineUserUtils.refuseInvite(userid, orgi);
		// OnlineUserUtils.sendWebIMClients(userid , "refuse");
		Page<InviteRecord> inviteRecordList = inviteRecordRes.findByUseridAndOrgi(userid, orgi,
				new PageRequest(0, 1, Direction.DESC, "createtime"));
		if (inviteRecordList.getContent() != null && inviteRecordList.getContent().size() > 0) {
			InviteRecord record = inviteRecordList.getContent().get(0);
			record.setUpdatetime(new Date());
			record.setResponsetime((int) (System.currentTimeMillis() - record.getCreatetime().getTime()));
			record.setResult(MainContext.OnlineUserInviteStatus.REFUSE.toString());
			inviteRecordRes.save(record);
		}
		return;
	}

	@RequestMapping("/satis")
	@Menu(type = "im", subtype = "satis", access = true)
	public void satis(HttpServletRequest request, HttpServletResponse response, @Valid AgentServiceSatis satis)
			throws Exception {
		if (satis != null && StringUtils.isNotBlank(satis.getId())) {
			int count = agentServiceSatisRes.countById(satis.getId());
			if (count == 1) {
				if (StringUtils.isNotBlank(satis.getSatiscomment()) && satis.getSatiscomment().length() > 255) {
					satis.setSatiscomment(satis.getSatiscomment().substring(0, 255));
				}
				satis.setSatisfaction(true);
				satis.setSatistime(new Date());
				agentServiceSatisRes.save(satis);
			}
		}
		return;
	}

	@RequestMapping("/image/upload")
	@Menu(type = "im", subtype = "image", access = true)
	public ModelAndView upload(ModelMap map, HttpServletRequest request,
	                           @RequestParam(value = "imgFile", required = false) MultipartFile multipart, @Valid String channel,
	                           @Valid String userid, @Valid String username, @Valid String appid, @Valid String orgi, @Valid String paste)
			throws IOException {
		ModelAndView view = request(super.createRequestPageTempletResponse("/apps/im/upload"));
		UploadStatus upload = null;

		////// 每个IP一天最多上传100张图 sin 2019-08-17 18:30:00 start
		if (multipart.getSize() > uploadMaxSize) {
			upload = new UploadStatus("上传图片过大，不能超过" + (uploadMaxSize / (1024 * 1024)) + "M");
			map.addAttribute("upload", upload);
			return view;
		}

		String ip = ExtUtils.getIpAddress(request);
		String key = ExtUtils.buildKey(ExtUtils.ext_lock_ip, ExtUtils.image, String.valueOf(ExtUtils.getCurrentDay()));
		String times = redisHashOps.get(key, ip);
		if (StringUtil.isNotEmpty(times) && Integer.parseInt(times) > uploadMaxNum) {
			upload = new UploadStatus("你的IP今日发送图片已达到上限数量：");
			map.addAttribute("upload", upload);
			return view;
		}
		////// 每个IP一天最多上传100张图 sin 2019-08-17 18:30:00 end
		String fileName = null;
		if (multipart != null && multipart.getOriginalFilename().lastIndexOf(".") > 0
				&& StringUtils.isNotBlank(userid)) {
			File uploadDir = new File(path, "upload");
			if (!uploadDir.exists()) {
				uploadDir.mkdirs();
			}

			String fileid = MainUtils.getUUID();
			StreamingFile sf = new StreamingFile();
			sf.setId(fileid);
			sf.setName(multipart.getOriginalFilename());
			sf.setMime(multipart.getContentType());
			if (multipart.getContentType() != null
					&& multipart.getContentType().indexOf(Constants.ATTACHMENT_TYPE_IMAGE) >= 0) {
				// 检查文件格式
				String invalid = StreamingFileUtils.getInstance().validate(Constants.ATTACHMENT_TYPE_IMAGE,
						multipart.getOriginalFilename());
				if (invalid == null) {
					fileName = "upload/" + fileid + "_original";
					File imageFile = new File(path, fileName);
					FileCopyUtils.copy(multipart.getBytes(), imageFile);
					String thumbnailsFileName = "upload/" + fileid;
					File thumbnail = new File(path, thumbnailsFileName);
					MainUtils.processImage(thumbnail, imageFile);

					// 存储数据库
					sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
					sf.setThumbnail(jpaBlobHelper.createBlobWithFile(thumbnail));
					streamingFileRepository.save(sf);
					String fileUrl = "/res/image.html?id=" + fileid;
					upload = new UploadStatus("0", fileUrl);

					if (paste == null) {
						if (StringUtils.isNotBlank(channel)) {
							RichMediaUtils.uploadImageWithChannel(fileUrl, fileid, (int) multipart.getSize(),
									multipart.getName(), channel, userid, username, appid, orgi);
						} else {
							RichMediaUtils.uploadImage(fileUrl, fileid, (int) multipart.getSize(), multipart.getName(),
									userid);
						}
					}
				} else {
					upload = new UploadStatus(invalid);
				}
			} else {
				String invalid = StreamingFileUtils.getInstance().validate(Constants.ATTACHMENT_TYPE_FILE,
						multipart.getOriginalFilename());
				if (invalid == null) {
					// 存储数据库
					sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
					streamingFileRepository.save(sf);

					// 存储到本地硬盘
					String id = processAttachmentFile(multipart, fileid, request);
					upload = new UploadStatus("0", "/res/file.html?id=" + id);
					String file = "/res/file.html?id=" + id;

					File tempFile = new File(multipart.getOriginalFilename());
					if (StringUtils.isNotBlank(channel)) {
						RichMediaUtils.uploadFileWithChannel(file, (int) multipart.getSize(), tempFile.getName(),
								channel, userid, username, appid, orgi, id);
					} else {
						RichMediaUtils.uploadFile(file, (int) multipart.getSize(), tempFile.getName(), userid, id);
					}
				} else {
					upload = new UploadStatus(invalid);
				}
			}
		} else {
			upload = new UploadStatus("请选择文件");
		}
		map.addAttribute("upload", upload);
		////// 每个IP一天最多上传100张图 sin 2019-08-17 18:30:00 start
		if ("0".equals(upload.getError())) {
			try {
				redisHashOps.increment(key, ip, 1);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		////// 每个IP一天最多上传100张图 sin 2019-08-17 18:30:00 end
		return view;
	}

	private String processAttachmentFile(final MultipartFile file, final String fileid, HttpServletRequest request)
			throws IOException {
		String id = null;
		if (file.getSize() > 0) { // 文件尺寸 限制 ？在 启动 配置中 设置 的最大值，其他地方不做限制
			AttachmentFile attachmentFile = new AttachmentFile();
			attachmentFile.setCreater(super.getUser(request).getId());
			attachmentFile.setOrgi(super.getOrgi(request));
			attachmentFile.setOrgan(super.getUser(request).getOrgan());
			attachmentFile.setModel(MainContext.ModelType.WEBIM.toString());
			attachmentFile.setFilelength((int) file.getSize());
			if (file.getContentType() != null && file.getContentType().length() > 255) {
				attachmentFile.setFiletype(file.getContentType().substring(0, 255));
			} else {
				attachmentFile.setFiletype(file.getContentType());
			}
			File uploadFile = new File(file.getOriginalFilename());
			if (uploadFile.getName() != null && uploadFile.getName().length() > 255) {
				attachmentFile.setTitle(uploadFile.getName().substring(0, 255));
			} else {
				attachmentFile.setTitle(uploadFile.getName());
			}
			if (StringUtils.isNotBlank(attachmentFile.getFiletype())
					&& attachmentFile.getFiletype().indexOf("image") >= 0) {
				attachmentFile.setImage(true);
			}
			attachmentFile.setFileid(fileid);
			attachementRes.save(attachmentFile);
			FileUtils.writeByteArrayToFile(new File(path, "upload/" + fileid), file.getBytes());
			id = attachmentFile.getId();
		}
		return id;
	}

	/**
	 * 客服咨询充值信息页面跳转
	 */
	@RequestMapping("/payInfoView")
	public ModelAndView goPayInfoView(Integer payFunctionId, String kefuPin) {
		ModelAndView view = request(super.createRequestPageTempletResponse("/apps/im/payInfoView"));

		//////////----->只显示对应客服录入的支付方式    by Wayne on 2019/9/13 14:07   start--->
		final UkPayMsgEntity ukPayMsgEntity = ukPayMsgRepository.findByIdAndStatus(payFunctionId, CommonConstant.NORMAL_STATUS);
		//////////<-----只显示对应客服录入的支付方式    by Wayne on 2019/9/13 14:07    <---end

		if (ukPayMsgEntity == null) {
			throw new RuntimeException("您选择的客服暂无对应的充值方式,请选择其他客服!");
		}


		String name = ukPayMsgEntity.getName();
		Integer type = ukPayMsgEntity.getType();
		String payInfoMsg = ukPayMsgEntity.getMsg();
		// 随机获取相关账号,加上权重功能
		List<UkPayAccountEntity> ukPayAccountEntityList = ukPayAccountRepository.findByPayFuctionIdAndTypeAndStatus(payFunctionId, type, CommonConstant.NORMAL_STATUS);
		if (ukPayAccountEntityList == null || ukPayAccountEntityList.size() == 0) {
			if (type == 1) {
				throw new RuntimeException("您选择的客服暂无" + name + "账号,请选择其他客服!");
			} else {
				throw new RuntimeException("您选择的客服暂无" + name + "二维码,请选择其他客服!");
			}
		}

		final UkPayAccountEntity ukPayAccountEntity = getPayAccountByWeightsRandomly(ukPayAccountEntityList);
		final String account = ukPayAccountEntity.getAccount();
		final String accountUsername = ukPayAccountEntity.getAccountUsername();
		final String accountBank = ukPayAccountEntity.getAccountBank();

		view.addObject("account", account);
		view.addObject("accountUsername", accountUsername);
		view.addObject("accountBank", accountBank);
		view.addObject("payInfoMsg", payInfoMsg);


		//////////----->实现不同支付方式对应不同的背景色    by Wayne on 2019/9/24 16:32   start--->
		if (StringUtils.contains(name, CommonConstant.ALIPAY_NAME)) {
			view.addObject("backgroundColor", CommonConstant.ALIPAY_COLOR);
		} else if (StringUtils.contains(name, CommonConstant.WECHATPAY_NAME)) {
			view.addObject("backgroundColor", CommonConstant.WECHATPAY_COLOR);
		} else {
			view.addObject("backgroundColor", CommonConstant.BANKCANDPAY_COLOR);
		}
		//////////<-----实现不同支付方式对应不同的背景色    by Wayne on 2019/9/24 16:32    <---end


		return view;
	}
}