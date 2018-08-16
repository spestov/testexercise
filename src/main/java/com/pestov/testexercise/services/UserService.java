package com.pestov.testexercise.services;

import com.pestov.testexercise.dto.BookSharingDto;
import com.pestov.testexercise.dto.UserDto;
import com.pestov.testexercise.models.Book;
import com.pestov.testexercise.models.BookSharing;
import com.pestov.testexercise.models.CustomUser;
import com.pestov.testexercise.repositories.BookRepository;
import com.pestov.testexercise.repositories.BookSharingRepository;
import com.pestov.testexercise.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.pestov.testexercise.conf.JWTAuthorizationFilter.getLoggedUserId;


@Service
@Slf4j
public class UserService implements IUserService {

	private final UserRepository userRepository;
	private final IRegTokenService regTokenService;
	private final IEmailService emailService;
	private final BookSharingRepository bookSharingRepository;
	private final BCryptPasswordEncoder bCryptPasswordEncoder;
	private final BookRepository bookRepository;


	public UserService(UserRepository userRepository, IRegTokenService regTokenService, IEmailService emailService,
					   BookSharingRepository bookSharingRepository, BCryptPasswordEncoder bCryptPasswordEncoder,
					   BookRepository bookRepository) {
		this.userRepository = userRepository;
		this.regTokenService = regTokenService;
		this.emailService = emailService;
		this.bookSharingRepository = bookSharingRepository;
		this.bCryptPasswordEncoder = bCryptPasswordEncoder;
		this.bookRepository = bookRepository;
	}

	@Value("${spring.application.url}")
	private String applicationUrl;

	@Override
	@Transactional
	public CustomUser registerNewUser(String userDto) {
		final CustomUser customUser = new CustomUser();
		UserDto dto = new UserDto(userDto);
		customUser.setEmail(dto.getEmail());
		customUser.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
		userRepository.save(customUser);
		String token = regTokenService.saveNewRegToken(customUser);
		emailService.sendSimpleMessage(
				dto.getEmail(),
				"Подтверждение регистрации",
				applicationUrl.concat("/signup/confirmEmail?token=").concat(token)
		);
		log.info("Email sent to ".concat(dto.getEmail()));
		return customUser;
	}

	public List<UserDto> getUsers() {
		List<CustomUser> usersList = userRepository.findAll();
		List<UserDto> usersDtoList = new ArrayList<>();
		for (CustomUser user : usersList) {
			usersDtoList.add(new UserDto(user.getEmail(), user.getId()));
		}
		return usersDtoList;
	}

	public BookSharing createBookSharingRequest(BookSharingDto bookSharingDto) {
		CustomUser owner = userRepository.getOne(bookSharingDto.getOwnerUserId());
		CustomUser asker = userRepository.getOne(bookSharingDto.getAskingUserId());
		Book book = bookRepository.getOne(bookSharingDto.getBook_id());
		BookSharing bookSharing = new BookSharing(owner, asker, book);
		bookSharingRepository.save(bookSharing);
		return bookSharing;
	}

	public List<BookSharingDto> getMyRequests() {
		List<BookSharing> myRequests = bookSharingRepository.findAllByOwnerUserIdAndAllowedIsFalse(getLoggedUserId());
		List<BookSharingDto> myRequestsDto = null;
		for (BookSharing bookSharing: myRequests) {
			BookSharingDto booksharingDto = new BookSharingDto();
			booksharingDto.setAskingUsername(bookSharing.getAskingUser().getEmail());
			booksharingDto.setBookName(bookSharing.getBook().getName());
			booksharingDto.setBookshelfName(bookSharing.getBook().getBookshelf().getName());
			booksharingDto.setId(bookSharing.getId());
			myRequestsDto.add(booksharingDto);
		}
		return myRequestsDto;
	}

	public BookSharing allowBooksharingRequestById(Long booksharingId, BookSharingDto bookSharingDto) {
		BookSharing bookSharing = bookSharingRepository.getOne(booksharingId);
		bookSharing.setAllowed(true);
		if (bookSharingDto.getExpireDate() != null) {
			bookSharing.setExpireDate(bookSharingDto.getExpireDate());
		}
		bookSharingRepository.save(bookSharing);
		return bookSharing;
	}

	public BookSharing refuseBooksharingRequestById(Long booksharingId, BookSharingDto bookSharingDto) {
		BookSharing bookSharing = bookSharingRepository.getOne(booksharingId);
		bookSharing.setAllowed(false);
		bookSharing.setRefuseDescription(bookSharingDto.getRefuseDescription());
		bookSharingRepository.save(bookSharing);
		return bookSharing;
	}

	public void deleteExpiredBooksharings(LocalDate yesterday) {
		List<BookSharing> expiredList = bookSharingRepository.findAllByExpireDateEquals(yesterday);
		for (BookSharing bookSharing : expiredList) {
			bookSharing.setAllowed(false);
			bookSharingRepository.save(bookSharing);
		}
	}

	public List<BookSharing> myRefusedRequests() {
		return bookSharingRepository.findAllByAskingUserIdAndAllowedIsFalse(getLoggedUserId());
	}

	public List<BookSharing> mySharedBooks() {
		return bookSharingRepository.findAllByAskingUserIdAndAllowedIsTrue(getLoggedUserId());
	}

	public boolean checkBookShared(Long bookId) {
		return bookSharingRepository.findByAskingUserIdAndBookId(getLoggedUserId(), bookId).isAllowed();
	}

	public BookSharing findBooksharingByLoggedAskingUserIdAndBookId(Long bookId) {
		return bookSharingRepository.findByAskingUserIdAndBookId(getLoggedUserId(), bookId);
	}
}
