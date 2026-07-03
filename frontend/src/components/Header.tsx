// import '../styles/header.css';

function Header() {
  return (
    <header className='header'>
      <div className='header-inner'>
        <h1 className='logo'>PULSE</h1>

        <nav className='navigation'>
          <button type="button">홈</button>
          <button type="button">로그인</button>
          <button type="button">회원가입</button>
        </nav>
      </div>
    </header>
  );
}

export default Header;